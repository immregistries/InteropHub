package org.airahub.interophub.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * Reads an xlsx or CSV roster and previews what an import would add. Column
 * positions come from the upload mapping, not from fixed header names.
 * The file is copied to a temp file before it is read, and that copy is deleted
 * before this method returns. Nothing from the file is written under the repo.
 */
public final class InitiativeImport {

    public static final int MAX_ROWS = 2000;

    public record DataRow(int rowNumber, List<String> cells) {
    }

    public record ParsedSheet(List<String> headers, List<DataRow> rows) {
    }

    public record ColumnMapping(int contact, int email, int organization, int organizationType, int notes)
            implements java.io.Serializable {
    }

    public enum Action {
        ADD_MEMBER,
        ADD_ORGANIZATION,
        SKIP,
        ERROR
    }

    public record PreviewRow(int rowNumber, String contactName, String email, String organizationName,
            String organizationTypeCode, String notes, Action action, boolean createOrganization,
            boolean addToInitiative, String detail) implements java.io.Serializable {
    }

    public record Preview(List<PreviewRow> rows) implements java.io.Serializable {
        public int memberCount() {
            return count(Action.ADD_MEMBER);
        }

        public int organizationCount() {
            int count = 0;
            for (PreviewRow row : rows) {
                if (row.addToInitiative()) {
                    count++;
                }
            }
            return count;
        }

        public int errorCount() {
            return count(Action.ERROR);
        }

        private int count(Action action) {
            int count = 0;
            for (PreviewRow row : rows) {
                if (row.action() == action) {
                    count++;
                }
            }
            return count;
        }
    }

    public record KnownOrganization(String normalizedName, String typeCode, boolean onInitiative) {
    }

    public record KnownMember(String emailNormalized) {
    }

    private InitiativeImport() {
    }

    public static ParsedSheet parse(String filename, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Choose a file to import.");
        }
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        String suffix = lower.endsWith(".csv") ? ".csv" : ".xlsx";
        if (!lower.endsWith(".csv") && !lower.endsWith(".xlsx") && !lower.endsWith(".xlsm")) {
            throw new IllegalArgumentException("Import a .xlsx or .csv file.");
        }
        Path temp = null;
        try {
            temp = Files.createTempFile("initiative-import-", suffix);
            Files.write(temp, bytes);
            if (lower.endsWith(".csv")) {
                return parseCsv(Files.readString(temp, StandardCharsets.UTF_8));
            }
            try (InputStream in = Files.newInputStream(temp); Workbook workbook = WorkbookFactory.create(in)) {
                return parseWorkbook(workbook);
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("Could not read that file.");
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // The temp copy is outside the repo. A failed delete does not keep the upload.
                }
            }
        }
    }

    public static ColumnMapping suggest(List<String> headers) {
        int contact = -1;
        int email = -1;
        int organization = -1;
        int organizationType = -1;
        int notes = -1;
        if (headers != null) {
            for (int i = 0; i < headers.size(); i++) {
                String header = compact(headers.get(i));
                if (header.isEmpty()) {
                    continue;
                }
                if (email < 0 && header.contains("email")) {
                    email = i;
                } else if (organizationType < 0 && (header.contains("organizationtype") || header.contains("orgtype")
                        || header.equals("type"))) {
                    organizationType = i;
                } else if (organization < 0 && (header.contains("organization") || header.equals("org")
                        || header.equals("company"))) {
                    organization = i;
                } else if (notes < 0 && header.contains("note")) {
                    notes = i;
                } else if (contact < 0 && (header.contains("contact") || header.equals("name"))) {
                    contact = i;
                }
            }
        }
        return new ColumnMapping(contact, email, organization, organizationType, notes);
    }

    public static String mappingError(ColumnMapping mapping) {
        if (mapping == null || (mapping.contact() < 0 && mapping.email() < 0 && mapping.organization() < 0)) {
            return "Map a contact, email, or organization column.";
        }
        return null;
    }

    public static Preview preview(ParsedSheet sheet, ColumnMapping mapping, List<KnownOrganization> organizations,
            List<KnownMember> members) {
        String mappingProblem = mappingError(mapping);
        if (mappingProblem != null) {
            throw new IllegalArgumentException(mappingProblem);
        }
        if (sheet == null) {
            throw new IllegalArgumentException("Choose a file to import.");
        }
        if (sheet.rows().size() > MAX_ROWS) {
            throw new IllegalArgumentException("Import files are limited to " + MAX_ROWS + " rows.");
        }
        Set<String> knownOrgs = new HashSet<>();
        Set<String> onInitiative = new HashSet<>();
        if (organizations != null) {
            for (KnownOrganization organization : organizations) {
                if (organization.normalizedName() != null && !organization.normalizedName().isBlank()) {
                    knownOrgs.add(organization.normalizedName());
                    if (organization.onInitiative()) {
                        onInitiative.add(organization.normalizedName());
                    }
                }
            }
        }
        Set<String> knownEmails = new HashSet<>();
        if (members != null) {
            for (KnownMember member : members) {
                if (member.emailNormalized() != null && !member.emailNormalized().isBlank()) {
                    knownEmails.add(member.emailNormalized());
                }
            }
        }
        Set<String> emailsInFile = new HashSet<>();
        List<PreviewRow> rows = new ArrayList<>();
        for (DataRow dataRow : sheet.rows()) {
            rows.add(previewRow(dataRow, mapping, knownOrgs, onInitiative, knownEmails, emailsInFile));
        }
        return new Preview(rows);
    }

    private static PreviewRow previewRow(DataRow dataRow, ColumnMapping mapping, Set<String> knownOrgs,
            Set<String> onInitiative, Set<String> knownEmails, Set<String> emailsInFile) {
        String contact = cell(dataRow.cells(), mapping.contact());
        String email = cell(dataRow.cells(), mapping.email());
        String organization = cell(dataRow.cells(), mapping.organization());
        String rawType = cell(dataRow.cells(), mapping.organizationType());
        String notes = cell(dataRow.cells(), mapping.notes());
        boolean hasContact = !contact.isBlank() || !email.isBlank();
        boolean hasOrganization = !organization.isBlank();
        if (!hasContact && !hasOrganization) {
            return row(dataRow, contact, email, organization, null, notes, Action.SKIP, false, false, "Empty row.");
        }
        String emailNormalized = EsNormalizer.normalizeEmail(email);
        if (!email.isBlank() && (emailNormalized == null || !emailNormalized.contains("@"))) {
            return row(dataRow, contact, email, organization, null, notes, Action.ERROR, false, false,
                    "Email address is not valid.");
        }

        boolean createOrganization = false;
        boolean addToInitiative = false;
        String typeCode = null;
        if (hasOrganization) {
            String key = OrganizationDirectory.normalizeName(organization);
            if (!knownOrgs.contains(key)) {
                OrganizationDirectory.ResolvedType resolved = OrganizationDirectory.resolveImportedType(rawType);
                if (resolved.kind() == OrganizationDirectory.TypeMatchKind.BLANK) {
                    return row(dataRow, contact, email, organization, null, notes, Action.ERROR, false, false,
                            "Organization type is required to add " + organization + ".");
                }
                if (resolved.kind() == OrganizationDirectory.TypeMatchKind.UNKNOWN) {
                    return row(dataRow, contact, email, organization, null, notes, Action.ERROR, false, false,
                            "Unrecognized organization type: " + rawType + ".");
                }
                createOrganization = true;
                addToInitiative = true;
                typeCode = resolved.code();
                knownOrgs.add(key);
                onInitiative.add(key);
            } else if (!onInitiative.contains(key)) {
                addToInitiative = true;
                onInitiative.add(key);
            }
        }

        if (!hasContact) {
            if (!createOrganization && !addToInitiative) {
                if (notes.isBlank()) {
                    return row(dataRow, contact, email, organization, null, notes, Action.SKIP, false, false,
                            "Already on this initiative.");
                }
                return row(dataRow, contact, email, organization, null, notes, Action.ADD_ORGANIZATION, false, false,
                        "Already on this initiative. The note is saved on the organization.");
            }
            String detail = notes.isBlank()
                    ? "Organization only."
                    : "Organization only. The note is saved on the organization.";
            return row(dataRow, contact, email, organization, typeCode, notes, Action.ADD_ORGANIZATION,
                    createOrganization, addToInitiative, detail);
        }

        if (emailNormalized != null) {
            if (emailsInFile.contains(emailNormalized)) {
                rollbackOrg(createOrganization, addToInitiative, organization, knownOrgs, onInitiative);
                return row(dataRow, contact, email, organization, typeCode, notes, Action.ERROR, false, false,
                        "Duplicate email in this file.");
            }
            if (knownEmails.contains(emailNormalized)) {
                rollbackOrg(createOrganization, addToInitiative, organization, knownOrgs, onInitiative);
                return row(dataRow, contact, email, organization, typeCode, notes, Action.SKIP, false, false,
                        "Already on the roster.");
            }
            emailsInFile.add(emailNormalized);
            knownEmails.add(emailNormalized);
        }

        String detail = notes.isBlank()
                ? "Will add this person."
                : "Will add this person. Notes become their first timeline entry.";
        if (createOrganization) {
            detail = "Will add the organization. " + detail;
        } else if (addToInitiative) {
            detail = "Will add the organization to this initiative. " + detail;
        }
        return row(dataRow, contact, email, organization, typeCode, notes, Action.ADD_MEMBER, createOrganization,
                addToInitiative, detail);
    }

    private static void rollbackOrg(boolean createdHere, boolean addedHere, String organization, Set<String> knownOrgs,
            Set<String> onInitiative) {
        if (!createdHere && !addedHere) {
            return;
        }
        String key = OrganizationDirectory.normalizeName(organization);
        if (createdHere) {
            knownOrgs.remove(key);
        }
        onInitiative.remove(key);
    }

    private static PreviewRow row(DataRow dataRow, String contact, String email, String organization, String typeCode,
            String notes, Action action, boolean createOrganization, boolean addToInitiative, String detail) {
        return new PreviewRow(dataRow.rowNumber(), blankToNull(contact), blankToNull(email),
                blankToNull(organization), typeCode, blankToNull(notes), action, createOrganization, addToInitiative,
                detail);
    }

    static ParsedSheet parseCsv(String text) {
        String source = text == null ? "" : text;
        if (source.startsWith("\uFEFF")) {
            source = source.substring(1);
        }
        List<List<String>> grid = readCsvGrid(source);
        return sheetFromGrid(grid);
    }

    private static ParsedSheet parseWorkbook(Workbook workbook) {
        if (workbook.getNumberOfSheets() == 0) {
            throw new IllegalArgumentException("The workbook has no sheets.");
        }
        Sheet sheet = workbook.getSheetAt(0);
        DataFormatter formatter = new DataFormatter();
        int width = 0;
        for (Row row : sheet) {
            if (row.getLastCellNum() > width) {
                width = row.getLastCellNum();
            }
        }
        List<List<String>> grid = new ArrayList<>();
        for (int r = sheet.getFirstRowNum(); r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            List<String> cells = new ArrayList<>();
            for (int c = 0; c < width; c++) {
                Cell cell = row == null ? null : row.getCell(c);
                cells.add(cell == null ? "" : formatter.formatCellValue(cell).trim());
            }
            grid.add(cells);
        }
        return sheetFromGrid(grid);
    }

    private static ParsedSheet sheetFromGrid(List<List<String>> grid) {
        if (grid.isEmpty() || isBlank(grid.get(0))) {
            throw new IllegalArgumentException("The file needs a header row.");
        }
        List<String> headers = grid.get(0);
        List<DataRow> rows = new ArrayList<>();
        for (int i = 1; i < grid.size(); i++) {
            if (!isBlank(grid.get(i))) {
                rows.add(new DataRow(i + 1, grid.get(i)));
            }
        }
        return new ParsedSheet(headers, rows);
    }

    private static List<List<String>> readCsvGrid(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                row.add(field.toString().trim());
                field.setLength(0);
            } else if (c == '\n') {
                row.add(field.toString().trim());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else if (c != '\r') {
                field.append(c);
            }
        }
        if (inQuotes) {
            throw new IllegalArgumentException("The CSV file has an unterminated quote.");
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString().trim());
            rows.add(row);
        }
        return rows;
    }

    private static boolean isBlank(List<String> cells) {
        for (String cell : cells) {
            if (cell != null && !cell.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static String cell(List<String> cells, int index) {
        if (cells == null || index < 0 || index >= cells.size() || cells.get(index) == null) {
            return "";
        }
        return cells.get(index).trim();
    }

    private static String compact(String header) {
        if (header == null) {
            return "";
        }
        return header.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
