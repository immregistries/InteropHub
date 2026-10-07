package org.airahub.interophub.service;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import javax.xml.stream.*;
import org.apache.poi.poifs.filesystem.*;
import org.apache.poi.hslf.usermodel.HSLFSlideShowImpl;
import org.apache.poi.hslf.record.VBAInfoAtom;

/** Bounded validation of accepted containers, not a malware scanner. */
public final class StoredFileValidation {
    public static final long MAX_BYTES = 25L * 1024 * 1024;
    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("png", "image/png"), Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"), Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"), Map.entry("pdf", "application/pdf"),
            Map.entry("txt", "text/plain"), Map.entry("doc", "application/msword"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"));

    private StoredFileValidation() { }

    public static String filename(String submitted) {
        if (submitted == null) {
            throw new IllegalArgumentException("A filename is required.");
        }
        String name = submitted.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        if (name.isBlank() || name.length() > 255 || name.chars().anyMatch(c -> Character.isISOControl(c))) {
            throw new IllegalArgumentException("Filename must contain 1-255 characters and no control characters.");
        }
        return name;
    }

    public static String validate(Path file, String filename, String declaredType) throws IOException {
        long size = Files.size(file);
        if (size == 0 || size > MAX_BYTES) {
            throw new IllegalArgumentException("Choose a non-empty file no larger than 25 MiB.");
        }
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String type = TYPES.get(extension);
        if (type == null) {
            throw new IllegalArgumentException("Only PNG, JPEG, WebP, GIF, PDF, TXT, DOC/DOCX and PPT/PPTX are allowed.");
        }
        String declared = declaredType == null ? "" : declaredType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!declared.isEmpty() && !declared.equals("application/octet-stream") && !declared.equals(type)) {
            throw new IllegalArgumentException("The declared content type does not match the filename.");
        }
        byte[] header;
        try (InputStream input = Files.newInputStream(file)) {
            header = input.readNBytes(32);
        }
        switch (extension) {
            case "png", "jpg", "jpeg", "gif" -> validateImage(file, type);
            case "webp" -> validateWebp(file, header);
            case "pdf" -> {
                if (!startsWith(header, "%PDF-")) {
                    throw new IllegalArgumentException("The file is not a PDF.");
                }
            }
            case "txt" -> validateText(file);
            case "docx", "pptx" -> validateOfficeZip(file, extension);
            case "doc", "ppt" -> validateLegacyOffice(file, extension);
            default -> throw new IllegalArgumentException("Unsupported file type.");
        }
        return type;
    }

    private static void validateImage(Path file, String type) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(file.toFile())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("The file is not a supported raster image.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!type.equals("image/" + format) && !(type.equals("image/jpeg") && format.equals("jpg"))) {
                    throw new IllegalArgumentException("Image content does not match its filename.");
                }
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels <= 0 || pixels > 40_000_000) {
                    throw new IllegalArgumentException("Raster images must not exceed 40 million pixels.");
                }
                if (reader.read(0) == null) {
                    throw new IllegalArgumentException("Image content could not be decoded.");
                }
            } finally {
                reader.dispose();
            }
        }
    }

    private static void validateWebp(Path file, byte[] header) throws IOException {
        if (header.length < 20 || !startsWith(header, "RIFF")
                || !new String(header, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")
                || unsignedInt(header, 4) + 8 != Files.size(file)) {
            throw new IllegalArgumentException("The file is not a WebP image.");
        }
        // Java's built-in image readers do not support WebP. Validate the RIFF chunk envelope.
        try (DataInputStream input = new DataInputStream(Files.newInputStream(file))) {
            input.skipNBytes(12);
            long remaining = Files.size(file) - 12;
            boolean image = false;
            while (remaining > 0) {
                byte[] chunk = input.readNBytes(8);
                if (chunk.length != 8) {
                    throw new IllegalArgumentException("Truncated WebP chunk.");
                }
                String name = new String(chunk, 0, 4, StandardCharsets.US_ASCII);
                long length = unsignedInt(chunk, 4);
                long padded = length + (length & 1);
                if (padded + 8 > remaining || length == 0) {
                    throw new IllegalArgumentException("Invalid WebP chunk length.");
                }
                if (Set.of("VP8 ", "VP8L", "ANMF").contains(name)) {
                    byte[] frame = input.readNBytes((int) Math.min(length, 16));
                    if (name.equals("VP8 ") && (frame.length < 10
                            || (frame[0] & 1) != 0 || (frame[3] & 255) != 0x9d
                            || (frame[4] & 255) != 1 || (frame[5] & 255) != 0x2a)) {
                        throw new IllegalArgumentException("Invalid WebP VP8 image header.");
                    }
                    if (name.equals("VP8L") && (frame.length < 5 || (frame[0] & 255) != 0x2f)) {
                        throw new IllegalArgumentException("Invalid WebP lossless image header.");
                    }
                    if (name.equals("ANMF") && frame.length < 16) {
                        throw new IllegalArgumentException("Invalid animated WebP frame.");
                    }
                    image = true;
                    input.skipNBytes(padded - frame.length);
                } else {
                    input.skipNBytes(padded);
                }
                remaining -= padded + 8;
            }
            if (!image) {
                throw new IllegalArgumentException("WebP contains no image payload.");
            }
        }
    }

    private static long unsignedInt(byte[] bytes, int offset) {
        return (bytes[offset] & 255L) | ((bytes[offset + 1] & 255L) << 8)
                | ((bytes[offset + 2] & 255L) << 16) | ((bytes[offset + 3] & 255L) << 24);
    }

    private static void validateText(Path file) throws IOException {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        try (Reader input = new InputStreamReader(Files.newInputStream(file), decoder)) {
            char[] buffer = new char[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                for (int i = 0; i < read; i++) {
                    if (Character.isISOControl(buffer[i]) && "\t\r\n".indexOf(buffer[i]) < 0) {
                        throw new IllegalArgumentException("TXT files must be UTF-8 text, not binary content.");
                    }
                }
            }
        }
    }

    private static void validateOfficeZip(Path file, String extension) throws IOException {
        String prefix = extension.equals("docx") ? "word/" : "ppt/";
        String main = prefix + (extension.equals("docx") ? "document.xml" : "presentation.xml");
        String mainType = extension.equals("docx")
                ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"
                : "application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml";
        try (ZipFile zip = new ZipFile(file.toFile())) {
            Set<String> names = new HashSet<>();
            long expanded = 0;
            int count = 0;
            for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements();) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                String lower = name.toLowerCase(Locale.ROOT);
                if (++count > 10000 || !names.add(name) || name.startsWith("/") || name.contains("\\")
                        || Arrays.asList(name.split("/")).contains("..")
                        || lower.contains("vba") || lower.contains("macros")) {
                    throw new IllegalArgumentException("Invalid or macro-enabled Office package.");
                }
                if (entry.isDirectory()) {
                    continue;
                }
                CRC32 crc = new CRC32();
                try (InputStream content = zip.getInputStream(entry)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = content.read(buffer)) != -1) {
                        expanded += read;
                        crc.update(buffer, 0, read);
                        if (expanded > 100L * 1024 * 1024) {
                            throw new IllegalArgumentException("Office package expands beyond the 100 MiB safety limit.");
                        }
                    }
                    if (crc.getValue() != entry.getCrc()) {
                        throw new IllegalArgumentException("Office package contains a corrupt entry.");
                    }
                }
            }
            if (!names.contains("[Content_Types].xml") || !names.contains("_rels/.rels") || !names.contains(main)) {
                throw new IllegalArgumentException("The file is not the requested Office document type.");
            }
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
            boolean correctMain = false;
            try (InputStream content = zip.getInputStream(zip.getEntry("[Content_Types].xml"))) {
                XMLStreamReader xml = factory.createXMLStreamReader(content);
                try {
                    while (xml.hasNext()) {
                        if (xml.next() == XMLStreamConstants.DTD) {
                            throw new IllegalArgumentException("Office package must not contain a DTD.");
                        }
                        if (xml.getEventType() != XMLStreamConstants.START_ELEMENT) {
                            continue;
                        }
                        String contentType = xml.getAttributeValue(null, "ContentType");
                        if (contentType != null && (contentType.toLowerCase(Locale.ROOT).contains("macro")
                                || contentType.toLowerCase(Locale.ROOT).contains("vba"))) {
                            throw new IllegalArgumentException("Macro-enabled Office files are not allowed.");
                        }
                        if (("Override".equals(xml.getLocalName()))
                                && ("/" + main).equals(xml.getAttributeValue(null, "PartName"))
                                && mainType.equals(contentType)) {
                            correctMain = true;
                        }
                    }
                } finally {
                    xml.close();
                }
            } catch (XMLStreamException ex) {
                throw new IllegalArgumentException("Invalid Office content-type manifest.", ex);
            }
            if (!correctMain) {
                throw new IllegalArgumentException("Office content does not match its filename.");
            }
        }
    }

    private static void validateLegacyOffice(Path file, String extension) throws IOException {
        try (POIFSFileSystem fs = new POIFSFileSystem(file.toFile(), true)) {
            DirectoryNode root = fs.getRoot();
            if (!root.hasEntry(extension.equals("doc") ? "WordDocument" : "PowerPoint Document")) {
                throw new IllegalArgumentException("Legacy Office content does not match its filename.");
            }
            rejectMacroEntries(root, 0);
            if (extension.equals("doc")) {
                try (InputStream word = root.createDocumentInputStream("WordDocument")) {
                    byte[] header = word.readNBytes(32);
                    if (header.length != 32 || (header[0] & 255) != 0xec || (header[1] & 255) != 0xa5) {
                        throw new IllegalArgumentException("Invalid legacy Word document header.");
                    }
                }
            } else {
                try (HSLFSlideShowImpl slides = new HSLFSlideShowImpl(root)) {
                    rejectPowerPointMacros(slides.getRecords(), 0);
                }
            }
        }
    }

    private static void rejectMacroEntries(DirectoryEntry directory, int depth) {
        if (depth > 64) {
            throw new IllegalArgumentException("Office storage nesting exceeds the safety limit.");
        }
        for (Entry entry : directory) {
            String name = entry.getName().toLowerCase(Locale.ROOT);
            if (name.contains("vba") || name.contains("macro")) {
                throw new IllegalArgumentException("Macro-enabled Office files are not allowed.");
            }
            if (entry instanceof DirectoryEntry child) {
                rejectMacroEntries(child, depth + 1);
            }
        }
    }

    private static void rejectPowerPointMacros(org.apache.poi.hslf.record.Record[] records, int depth) {
        if (depth > 64) {
            throw new IllegalArgumentException("PowerPoint record nesting exceeds the safety limit.");
        }
        if (records == null) {
            return;
        }
        for (org.apache.poi.hslf.record.Record record : records) {
            if (record instanceof VBAInfoAtom atom && atom.isHasMacros()) {
                throw new IllegalArgumentException("Macro-enabled PowerPoints are not allowed.");
            }
            rejectPowerPointMacros(record.getChildRecords(), depth + 1);
        }
    }

    private static boolean startsWith(byte[] bytes, String text) {
        return bytes.length >= text.length()
                && new String(bytes, 0, text.length(), StandardCharsets.US_ASCII).equals(text);
    }
}
