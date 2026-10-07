package org.airahub.interophub.servlet;

import jakarta.servlet.http.*;
import java.io.*;
import java.nio.channels.*;
import java.nio.file.NoSuchFileException;
import java.time.ZoneOffset;
import java.util.*;
import java.util.logging.*;
import org.airahub.interophub.config.ArtifactStorageConfig;
import org.airahub.interophub.dao.StoredFileDao;
import org.airahub.interophub.model.StoredFile;
import org.airahub.interophub.service.*;

/** Known opaque URLs are anonymous; this endpoint never lists files or accepts writes. */
public class StoredFileServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(StoredFileServlet.class.getName());
    private final StoredFileDao files;
    private final LocalFileStorageService local;

    public StoredFileServlet() {
        this(new StoredFileDao(), new LocalFileStorageService(ArtifactStorageConfig.current()));
    }

    StoredFileServlet(StoredFileDao files, LocalFileStorageService local) {
        this.files = files;
        this.local = local;
    }

    @Override
    public void init() {
        local.setDeploymentPath(getServletContext().getRealPath("/"));
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        serve(request, response, false);
    }

    @Override
    protected void doHead(HttpServletRequest request, HttpServletResponse response) throws IOException {
        serve(request, response, true);
    }

    private void serve(HttpServletRequest request, HttpServletResponse response, boolean head) throws IOException {
        response.setHeader("Cache-Control", "no-cache");
        String path = request.getPathInfo();
        String id = path == null || !path.startsWith("/") ? "" : path.substring(1);
        try {
            ArtifactStorageConfig.validateKey(id);
        } catch (IllegalArgumentException ex) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        try {
            StoredFile file = files.findByPublicId(id).orElse(null);
            if (file == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            if (file.getBackend() == StoredFile.Backend.BLOB) {
                response.setStatus(HttpServletResponse.SC_FOUND);
                response.setHeader("Location", ArtifactStorageConfig.blobReadUrl(
                        file.getBlobEndpoint(), file.getBlobContainer(), file.getStorageKey()));
                return;
            }
            if (file.getBackend() != StoredFile.Backend.LOCAL) {
                throw new IllegalStateException("Stored file has an invalid backend.");
            }
            FileChannel channel;
            try {
                channel = local.open(file.getStorageKey());
            } catch (NoSuchFileException ex) {
                // A replacement may have committed after our metadata read and cleaned up its old key.
                StoredFile latest = files.findByPublicId(id).orElseThrow(() -> ex);
                if (Objects.equals(latest.getStorageKey(), file.getStorageKey())
                        || latest.getBackend() != StoredFile.Backend.LOCAL) {
                    throw ex;
                }
                file = latest;
                channel = local.open(file.getStorageKey());
            }
            try (FileChannel input = channel) {
                if (input.size() != file.getSizeBytes()) {
                    throw new IOException("Stored file size does not match registered metadata.");
                }
                String etag = "\"" + file.getStorageKey() + "\"";
                long modified = file.getUploadedAt().toInstant(ZoneOffset.UTC).toEpochMilli() / 1000 * 1000;
                response.setHeader("ETag", etag);
                response.setDateHeader("Last-Modified", modified);
                response.setHeader("X-Content-Type-Options", "nosniff");
                response.setHeader("Content-Security-Policy", "sandbox; default-src 'none'");
                response.setContentType(file.getContentType());
                response.setHeader("Content-Disposition", StoredFileService.contentDisposition(
                        file.getOriginalFilename(), StoredFileService.inline(file)));
                String match = request.getHeader("If-None-Match");
                if (matchesEtag(match, etag)
                        || (match == null && modifiedSince(request) >= modified)) {
                    response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    return;
                }
                response.setContentLengthLong(file.getSizeBytes());
                if (!head) {
                    input.position(0);
                    InputStream stream = Channels.newInputStream(input);
                    stream.transferTo(response.getOutputStream());
                }
            }
        } catch (NoSuchFileException ex) {
            LOGGER.log(Level.WARNING, "Registered local file is missing: " + id, ex);
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "File content is missing.");
        } catch (IOException | RuntimeException ex) {
            LOGGER.log(Level.SEVERE, "Stored file could not be served: " + id, ex);
            if (!response.isCommitted()) {
                response.reset();
                response.setHeader("Cache-Control", "no-store");
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "File storage is unavailable.");
            }
        }
    }

    private static long modifiedSince(HttpServletRequest request) {
        try {
            return request.getDateHeader("If-Modified-Since");
        } catch (IllegalArgumentException ex) {
            LOGGER.fine("Ignoring malformed If-Modified-Since request header.");
            return -1;
        }
    }

    static boolean matchesEtag(String header, String etag) {
        if (header == null) {
            return false;
        }
        return Arrays.stream(header.split(",")).map(String::trim)
                .anyMatch(value -> value.equals("*") || value.equals(etag) || value.equals("W/" + etag));
    }
}
