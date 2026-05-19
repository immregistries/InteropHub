# Localhost / Test Environment Detection

This document describes how InteropHub detects that it is running in a local development (test) environment, and how to use that detection anywhere else in the codebase.

---

## How it works

The canonical signal is `hub_settings.external_base_url`. When that URL's host resolves to `localhost` or `127.0.0.1`, the application is considered to be running locally.

The detection lives in `PublicUrlService.isLocalhostMode()`:

```java
public boolean isLocalhostMode() {
    String url = resolveExternalBaseUrl();
    try {
        URI uri = URI.create(url);
        String host = uri.getHost();
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);
    } catch (IllegalArgumentException e) {
        return false;
    }
}
```

`resolveExternalBaseUrl()` reads `hub_settings` (active row first, then any row), falling back to `http://localhost:8080/hub` if no row exists — so a fresh database with no settings row also counts as localhost mode.

---

## Using it in a servlet

```java
boolean isLocalhost = new PublicUrlService().isLocalhostMode();
```

`PublicUrlService` has a no-arg constructor and creates its own `HubSettingDao` internally, so no injection is needed.

---

## Using it in an HTML page renderer

For anything that emits an HTML page, call the shared helper immediately after the `<body>` tag:

```java
out.println("<body>");
LocalEnvBannerRenderer.renderIfLocalhost(out);
// ... rest of page
```

`LocalEnvBannerRenderer` is package-private in the `servlet` package. If you are rendering from a class outside that package, call `PublicUrlService.isLocalhostMode()` directly and emit whatever markup you need.

---

## The visual indicator

`LocalEnvBannerRenderer.renderIfLocalhost(out)` emits a `<div class="env-ribbon">LOCAL</div>` when in localhost mode. The `.env-ribbon` CSS rule in `main.css` renders it as a red diagonal banner fixed to the top-right corner of the viewport (`position: fixed; transform: rotate(45deg); z-index: 9999`). It has `pointer-events: none` so it never interferes with clicks.

The ribbon is currently wired into:
- `AdminShellRenderer` — all admin pages
- `HomeServlet` — login page
- `WelcomeServlet` — welcome/dashboard (both admin and non-admin layouts)

---

## Changing the production URL

Set `hub_settings.external_base_url` to a non-localhost URL (e.g. `https://interophub.example.org/hub`) via the Admin → Hub Settings screen. Once saved, `isLocalhostMode()` returns `false` and the ribbon disappears on next page load.

---

## Adding detection to other pages

1. In the servlet, call `new PublicUrlService().isLocalhostMode()`.
2. Use the result to conditionally emit markup, set a response header, skip a feature, log a warning, etc.
3. If emitting HTML, call `LocalEnvBannerRenderer.renderIfLocalhost(out)` right after the `<body>` opening tag to keep the visual indicator consistent.
