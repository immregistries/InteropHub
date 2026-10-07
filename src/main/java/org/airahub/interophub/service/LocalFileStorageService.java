package org.airahub.interophub.service;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.logging.*;
import org.airahub.interophub.config.ArtifactStorageConfig;

public class LocalFileStorageService {
    private static final Logger LOGGER = Logger.getLogger(LocalFileStorageService.class.getName());
    private final ArtifactStorageConfig config;
    private Path deploymentRoot;

    public LocalFileStorageService(ArtifactStorageConfig config) { this.config = config; }

    public Path root(boolean writable) throws IOException {
        Path root = config.localRoot();
        if (deploymentRoot != null && root.startsWith(deploymentRoot)) {
            throw new IOException("Local artifact storage must be outside the deployed web application.");
        }
        for (Path path = root; path != null; path = path.getParent()) {
            if (Files.isSymbolicLink(path) || (Files.exists(path) && !path.toRealPath().equals(path))) {
                throw new IOException("Local storage must not use symlinks or redirected directories.");
            }
        }
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(root)
                || (writable && !Files.isWritable(root))) {
            throw new IOException("INTEROPHUB_ARTIFACTS_LOCAL_DIRECTORY must exist and be readable"
                    + (writable ? " and writable by Tomcat." : " by Tomcat."));
        }
        Path temporaryRoot = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        if (root.startsWith(temporaryRoot)) {
            throw new IOException("Local artifact storage must not reside in a temporary directory.");
        }
        return root;
    }

    public void setDeploymentPath(String deploymentPath) {
        deploymentRoot = deploymentPath == null ? null : Path.of(deploymentPath).toAbsolutePath().normalize();
    }

    public String writeProblem() {
        if (!config.hasLocalDirectory()) {
            return "Local storage is disabled. Set INTEROPHUB_ARTIFACTS_LOCAL_DIRECTORY.";
        }
        try {
            root(true);
            return null;
        } catch (IOException | IllegalStateException | InvalidPathException ex) {
            LOGGER.log(Level.WARNING, "Local artifact storage configuration is invalid", ex);
            return "Local storage is unavailable. Check INTEROPHUB_ARTIFACTS_LOCAL_DIRECTORY and Tomcat permissions.";
        }
    }

    public Path stage(InputStream input) throws IOException {
        Path staged = Files.createTempFile(root(true), "upload-", ".tmp");
        boolean complete = false;
        try {
            try (OutputStream output = Files.newOutputStream(staged)) {
                copyBounded(input, output);
            }
            complete = true;
            return staged;
        } finally {
            if (!complete) {
                Files.deleteIfExists(staged);
            }
        }
    }

    static long copyBounded(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        long count = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            count += read;
            if (count > StoredFileValidation.MAX_BYTES) {
                throw new IllegalArgumentException("File exceeds the 25 MiB limit.");
            }
            output.write(buffer, 0, read);
        }
        return count;
    }

    public Path publish(Path staged, String key) throws IOException {
        Path destination = path(key, true);
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Generated storage key already exists.");
        }
        try (FileChannel channel = FileChannel.open(staged, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
        // Never fall back to a non-atomic move that could expose partial bytes.
        return Files.move(staged, destination, StandardCopyOption.ATOMIC_MOVE);
    }

    public Path path(String key, boolean writable) throws IOException {
        ArtifactStorageConfig.validateKey(key);
        Path path = root(writable).resolve(key);
        if (Files.isSymbolicLink(path)
                || (Files.exists(path) && !path.toRealPath().equals(path))) {
            throw new IOException("Artifact key resolves through a symlink or redirected file.");
        }
        return path;
    }

    public FileChannel open(String key) throws IOException {
        Path path = path(key, false);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new NoSuchFileException("Stored file is missing.");
        }
        return FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
    }

    public void delete(String key) throws IOException {
        Files.deleteIfExists(path(key, true));
    }
}
