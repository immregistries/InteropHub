package org.airahub.interophub.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.airahub.interophub.dao.StoredFileDao;
import org.airahub.interophub.model.StoredFile;

/**
 * Puts a validated guide into the configured file backend as an attachment.
 * Callers receive only the stored-file id. The public id and any Blob URL stay
 * on the storage row and are not returned.
 */
final class GuideHtmlFiles implements GuideHostingService.Files {
    private final StoredFileService files;
    private final StoredFileDao dao;

    GuideHtmlFiles(StoredFileService files, StoredFileDao dao) {
        this.files = files;
        this.dao = dao;
    }

    GuideHtmlFiles() {
        this(new StoredFileService(), new StoredFileDao());
    }

    @Override
    public long store(byte[] html, long userId) throws IOException {
        StoredFile saved = files.storePrepared(new ByteArrayInputStream(html), "guide.html", "text/html", userId,
                true, dao::save);
        if (saved.getStoredFileId() == null) {
            throw new IOException("The guide could not be stored.");
        }
        return saved.getStoredFileId();
    }

    @Override
    public byte[] read(long storedFileId) throws IOException {
        StoredFile file = dao.findById(storedFileId).orElse(null);
        if (file == null) {
            throw new IOException("The hosted guide could not be read.");
        }
        try (InputStream input = files.open(file)) {
            return input.readAllBytes();
        }
    }
}
