#!/usr/bin/env python3
"""
Rebuild the local InteropHub MySQL database from a production backup.

Steps, in order:
  1. (--download only) Download the latest production backup.
  2. Drop and recreate the local database.
  3. Import the backup.
  4. Apply db/local_database_refresh.sql (production URLs/settings -> localhost).
  5. Apply db/unapplied_updates.sql, if non-empty.
  6. Apply every db/scripts/*_dev_setup.sql, in name order. Local only; never production.
  7. Regenerate db/schema.sql via mysqldump (unless --no-schema-export).

This is destructive: anything that exists only in the local database is lost.
See docs/local-dev-environment-setup.md and docs/database-release-practice.md.

Configuration comes from environment variables, optionally loaded from an env
file (KEY=VALUE lines). Real environment variables win over the file. By default
the file db/scripts/refresh_local_db.env is read if it exists (it is gitignored);
see db/scripts/refresh_local_db.env.example for every setting.

Examples:
    python db/scripts/refresh_local_db.py                 # reload from the backup already on disk
    python db/scripts/refresh_local_db.py --download      # fetch a fresh backup first
    python db/scripts/refresh_local_db.py --download --once-per-day   # for a scheduled job
"""
from __future__ import annotations

import argparse
import gzip
import json
import os
import shutil
import subprocess
import sys
from datetime import date, datetime
from pathlib import Path
from typing import Any


REPO_ROOT = Path(__file__).resolve().parents[2]
DB_DIR = REPO_ROOT / "db"
DEFAULT_ENV_FILE = Path(__file__).resolve().with_suffix(".env")
POST_REFRESH_SQL_PATH = DB_DIR / "local_database_refresh.sql"
UNAPPLIED_UPDATES_SQL_PATH = DB_DIR / "unapplied_updates.sql"
SCHEMA_SQL_PATH = DB_DIR / "schema.sql"
CHUNK_SIZE = 1024 * 1024

DEFAULTS = {
    "INTEROPHUB_BACKUP_URL": "https://informatics.immregistries.org/dumps/interophub_backup.sql.gz",
    "INTEROPHUB_BACKUP_USER": "",
    "INTEROPHUB_BACKUP_PASSWORD": "",
    # Kept outside the repo by default, next to the InteropHub checkout.
    "INTEROPHUB_BACKUP_PATH": str(REPO_ROOT.parent / "interophub_backup.sql.gz"),
    "INTEROPHUB_REFRESH_STATE_PATH": str(REPO_ROOT.parent / "refresh_interophub_db_state.json"),
    "INTEROPHUB_LOCAL_MYSQL_HOST": "127.0.0.1",
    "INTEROPHUB_LOCAL_MYSQL_PORT": "3306",
    "INTEROPHUB_LOCAL_MYSQL_USER": "root",
    "INTEROPHUB_LOCAL_MYSQL_PASSWORD": "",
    "INTEROPHUB_LOCAL_DB_NAME": "interophub",
}


def _print(message: str) -> None:
    text = f"[refresh_local_db] {message}\n"
    encoding = sys.stdout.encoding or "utf-8"
    sys.stdout.buffer.write(text.encode(encoding, errors="replace"))
    sys.stdout.flush()


class Config:
    def __init__(self, values: dict[str, str]) -> None:
        self.backup_url = values["INTEROPHUB_BACKUP_URL"]
        self.backup_user = values["INTEROPHUB_BACKUP_USER"]
        self.backup_password = values["INTEROPHUB_BACKUP_PASSWORD"]
        self.backup_path = Path(values["INTEROPHUB_BACKUP_PATH"])
        self.state_path = Path(values["INTEROPHUB_REFRESH_STATE_PATH"])
        self.mysql_host = values["INTEROPHUB_LOCAL_MYSQL_HOST"]
        self.mysql_port = values["INTEROPHUB_LOCAL_MYSQL_PORT"]
        self.mysql_user = values["INTEROPHUB_LOCAL_MYSQL_USER"]
        self.mysql_password = values["INTEROPHUB_LOCAL_MYSQL_PASSWORD"]
        self.database_name = values["INTEROPHUB_LOCAL_DB_NAME"]


def _read_env_file(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8-sig").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        values[key.strip()] = value
    return values


def _load_config(env_file: Path | None) -> Config:
    file_values: dict[str, str] = {}
    if env_file is not None:
        if not env_file.is_file():
            raise RuntimeError(f"Env file was not found: {env_file}")
        file_values = _read_env_file(env_file)
    elif DEFAULT_ENV_FILE.is_file():
        file_values = _read_env_file(DEFAULT_ENV_FILE)
        env_file = DEFAULT_ENV_FILE
    if env_file is not None:
        _print(f"Loaded settings from {env_file}")

    values = {}
    for key, default in DEFAULTS.items():
        values[key] = os.environ.get(key) or file_values.get(key) or default
    return Config(values)


def _require_tool(name: str) -> str:
    path = shutil.which(name)
    if not path:
        raise RuntimeError(f"Required tool not found on PATH: {name}")
    return path


def _run(args: list[str], *, stdin: Any = None, stdout: Any = None,
         env: dict[str, str] | None = None) -> subprocess.CompletedProcess[Any]:
    return subprocess.run(
        args,
        check=True,
        capture_output=stdout is None,
        text=True,
        stdin=stdin,
        stdout=stdout,
        env=env,
    )


def _load_state(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        _print(f"State file is invalid JSON, ignoring: {path}")
        return {}


def _save_state(path: Path, state: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(state, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def _mysql_env(config: Config) -> dict[str, str]:
    env = os.environ.copy()
    if config.mysql_password:
        env["MYSQL_PWD"] = config.mysql_password
    return env


def _connection_args(tool_path: str, config: Config) -> list[str]:
    return [
        tool_path,
        f"--host={config.mysql_host}",
        f"--port={config.mysql_port}",
        f"--user={config.mysql_user}",
        "--default-character-set=utf8mb4",
    ]


def _download_backup(curl_path: str, config: Config) -> None:
    if not config.backup_user or not config.backup_password:
        raise RuntimeError(
            "--download needs INTEROPHUB_BACKUP_USER and INTEROPHUB_BACKUP_PASSWORD to be set.")
    destination = config.backup_path
    destination.parent.mkdir(parents=True, exist_ok=True)
    # Download beside the real file first so a failed download never clobbers the last good backup.
    partial = destination.with_name(destination.name + ".partial")
    _run([
        curl_path,
        "--fail",
        "--silent",
        "--show-error",
        "--location",
        "--user",
        f"{config.backup_user}:{config.backup_password}",
        "--output",
        str(partial),
        config.backup_url,
    ])
    partial.replace(destination)


def _recreate_database(mysql_path: str, config: Config) -> None:
    name = config.database_name
    sql = (
        f"DROP DATABASE IF EXISTS `{name}`;"
        f"CREATE DATABASE `{name}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    )
    _run(_connection_args(mysql_path, config) + ["--execute", sql], env=_mysql_env(config))


def _import_backup(mysql_path: str, config: Config) -> None:
    args = _connection_args(mysql_path, config) + ["--binary-mode=1", config.database_name]
    process = subprocess.Popen(
        args,
        stdin=subprocess.PIPE,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.PIPE,
        env=_mysql_env(config),
    )
    try:
        assert process.stdin is not None
        with gzip.open(config.backup_path, "rb") as sql_stream:
            while True:
                chunk = sql_stream.read(CHUNK_SIZE)
                if not chunk:
                    break
                process.stdin.write(chunk)
        process.stdin.close()
        stderr = process.stderr.read() if process.stderr else b""
        return_code = process.wait()
    except Exception:
        process.kill()
        process.wait()
        raise

    if return_code != 0:
        detail = stderr.decode("utf-8", errors="replace").strip()
        raise RuntimeError(f"MySQL import failed with exit code {return_code}: {detail}")


def _apply_sql_file(mysql_path: str, config: Config, sql_path: Path) -> None:
    with sql_path.open("rb") as sql_file:
        _run(_connection_args(mysql_path, config) + [config.database_name],
             stdin=sql_file, env=_mysql_env(config))


def _apply_optional_sql(mysql_path: str, config: Config, sql_path: Path) -> bool:
    if not sql_path.exists():
        _print(f"Optional SQL file was not found, skipping: {sql_path}")
        return False
    if not sql_path.read_text(encoding="utf-8").strip():
        _print(f"Optional SQL file is empty, skipping: {sql_path}")
        return False
    _apply_sql_file(mysql_path, config, sql_path)
    return True


SCHEMA_SQL_BANNER = """-- ============================================================
-- AUTO-GENERATED FILE — DO NOT HAND-EDIT
-- Generated by db/scripts/refresh_local_db.py via
-- `mysqldump --no-data` against the local `interophub` database,
-- immediately after applying db/local_database_refresh.sql and
-- db/unapplied_updates.sql to a freshly restored production snapshot.
--
-- This is a point-in-time reference snapshot of the full schema, not a
-- migration and not consumed by the application (Hibernate only
-- validates the schema at startup: hibernate.hbm2ddl.auto=validate).
-- Schema changes belong in db/unapplied_updates.sql; this file will be
-- silently overwritten on the next refresh/restore run.
-- See docs/database-release-practice.md.
-- ============================================================

"""


def _export_schema(mysqldump_path: str, config: Config) -> None:
    with SCHEMA_SQL_PATH.open("w", encoding="utf-8", newline="\n") as schema_file:
        schema_file.write(SCHEMA_SQL_BANNER)
        # Flush before mysqldump writes to the same handle, or the banner lands at the end.
        schema_file.flush()
        _run(
            _connection_args(mysqldump_path, config)
            + [
                "--no-data",
                "--routines",
                "--events",
                "--triggers",
                "--single-transaction",
                config.database_name,
            ],
            stdout=schema_file,
            env=_mysql_env(config),
        )


def _parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Rebuild the local InteropHub database from a production backup.")
    parser.add_argument("--download", action="store_true",
                        help="download a fresh production backup before restoring")
    parser.add_argument("--once-per-day", action="store_true",
                        help="do nothing if a --download run already succeeded today (for scheduled jobs)")
    parser.add_argument("--no-schema-export", action="store_true",
                        help="do not regenerate db/schema.sql")
    parser.add_argument("--env-file", type=Path,
                        help=f"settings file to load (default: {DEFAULT_ENV_FILE.name} beside this script, if present)")
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = _parse_args(argv)
    try:
        config = _load_config(args.env_file)
    except Exception as exc:
        _print(f"Refresh failed: {exc}")
        return 1

    state = _load_state(config.state_path)
    today = date.today().isoformat()
    if args.once_per_day and state.get("last_successful_refresh_date") == today:
        _print("A download-and-refresh already completed successfully today. Skipping.")
        return 0

    try:
        mysql_path = _require_tool("mysql")
        mysqldump_path = None if args.no_schema_export else _require_tool("mysqldump")

        if args.download:
            curl_path = _require_tool("curl")
            _print(f"Downloading backup to {config.backup_path}")
            _download_backup(curl_path, config)
            _print("Backup download completed successfully.")
        elif not config.backup_path.is_file():
            raise RuntimeError(
                f"Local backup file was not found: {config.backup_path} (run with --download to fetch one)")

        _print(f"Recreating local database `{config.database_name}`")
        _recreate_database(mysql_path, config)

        _print(f"Importing {config.backup_path.name} into local database `{config.database_name}`")
        _import_backup(mysql_path, config)
        _print("Local database was replaced successfully.")

        _print(f"Applying local settings from {POST_REFRESH_SQL_PATH.name}")
        _apply_sql_file(mysql_path, config, POST_REFRESH_SQL_PATH)

        _print(f"Applying unapplied updates from {UNAPPLIED_UPDATES_SQL_PATH.name}")
        if _apply_optional_sql(mysql_path, config, UNAPPLIED_UPDATES_SQL_PATH):
            _print("Unapplied updates were applied successfully.")

        for setup in sorted((DB_DIR / "scripts").glob("*_dev_setup.sql")):
            _print(f"Applying local dev setup from {setup.name}")
            _apply_sql_file(mysql_path, config, setup)

        if mysqldump_path:
            _print(f"Exporting current schema to {SCHEMA_SQL_PATH}")
            _export_schema(mysqldump_path, config)

        if args.download:
            state.update({
                "last_download_path": str(config.backup_path),
                "last_successful_refresh_at": datetime.now().isoformat(timespec="seconds"),
                "last_successful_refresh_date": today,
            })
            _save_state(config.state_path, state)
        _print("Done.")
        return 0
    except subprocess.CalledProcessError as exc:
        stderr = (exc.stderr or "").strip() if isinstance(exc.stderr, str) else ""
        stdout = (exc.stdout or "").strip() if isinstance(exc.stdout, str) else ""
        detail = stderr or stdout or str(exc)
        return _fail(config, state, args, detail, exc.returncode or 1)
    except Exception as exc:
        return _fail(config, state, args, str(exc), 1)


def _fail(config: Config, state: dict[str, Any], args: argparse.Namespace, detail: str, code: int) -> int:
    if args.download:
        state.update({
            "last_failure_at": datetime.now().isoformat(timespec="seconds"),
            "last_failure_message": detail,
        })
        _save_state(config.state_path, state)
    _print(f"Refresh failed: {detail}")
    return code


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
