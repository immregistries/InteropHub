package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class InitiativeImportTest {

    @Test
    void sabrinaHeadersMapWithoutHardCodingColumnOrder() {
        InitiativeImport.ParsedSheet sheet = InitiativeImport.parse("interest.csv",
                """
                Organization Type,Notes,Organization,Email,Point of Contact
                Consultant,Emailed to ask,HLN,ada@example.org,Ada Lovelace
                School Information System,,Skyward,,
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        InitiativeImport.ColumnMapping mapping = InitiativeImport.suggest(sheet.headers());
        assertEquals(4, mapping.contact());
        assertEquals(3, mapping.email());
        assertEquals(2, mapping.organization());
        assertEquals(0, mapping.organizationType());
        assertEquals(1, mapping.notes());

        InitiativeImport.Preview preview = InitiativeImport.preview(sheet, mapping, List.of(), List.of());
        assertEquals(1, preview.memberCount());
        assertEquals(2, preview.organizationCount());
        assertEquals(0, preview.errorCount());

        InitiativeImport.PreviewRow person = preview.rows().get(0);
        assertEquals(InitiativeImport.Action.ADD_MEMBER, person.action());
        assertTrue(person.createOrganization());
        assertEquals("CONSULTANT", person.organizationTypeCode());
        assertEquals("Emailed to ask", person.notes());
        assertTrue(person.detail().contains("first timeline entry"));

        InitiativeImport.PreviewRow organization = preview.rows().get(1);
        assertEquals(InitiativeImport.Action.ADD_ORGANIZATION, organization.action());
        assertEquals("SCHOOL_INFORMATION_SYSTEM", organization.organizationTypeCode());
        assertNull(organization.contactName());
        assertNull(organization.email());
    }

    @Test
    void xlsxPreviewShowsNewOrganizationsPeopleAndProblemsBeforeCommit() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet();
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Point of Contact");
            header.createCell(1).setCellValue("Email");
            header.createCell(2).setCellValue("Organization");
            header.createCell(3).setCellValue("Organization Type");
            header.createCell(4).setCellValue("Notes");

            var existing = sheet.createRow(1);
            existing.createCell(0).setCellValue("Grace Hopper");
            existing.createCell(1).setCellValue("grace@example.org");
            existing.createCell(2).setCellValue("HLN");
            existing.createCell(4).setCellValue("Already talking");

            var missingType = sheet.createRow(2);
            missingType.createCell(2).setCellValue("Mystery Org");

            var badType = sheet.createRow(3);
            badType.createCell(2).setCellValue("Other Place");
            badType.createCell(3).setCellValue("Hospital");

            var duplicate = sheet.createRow(4);
            duplicate.createCell(1).setCellValue("grace@example.org");
            duplicate.createCell(2).setCellValue("HLN");

            workbook.write(out);
            bytes = out.toByteArray();
        }

        InitiativeImport.ParsedSheet sheet = InitiativeImport.parse("Proof of Concept Interest.xlsx", bytes);
        InitiativeImport.ColumnMapping mapping = InitiativeImport.suggest(sheet.headers());
        InitiativeImport.Preview preview = InitiativeImport.preview(sheet, mapping, List.of(
                new InitiativeImport.KnownOrganization("hln", "CONSULTANT", true)), List.of());

        InitiativeImport.PreviewRow person = preview.rows().get(0);
        assertEquals(InitiativeImport.Action.ADD_MEMBER, person.action());
        assertFalse(person.createOrganization());
        assertFalse(person.addToInitiative());
        assertEquals("Already talking", person.notes());

        assertEquals(InitiativeImport.Action.ERROR, preview.rows().get(1).action());
        assertTrue(preview.rows().get(1).detail().contains("Organization type is required"));

        assertEquals(InitiativeImport.Action.ERROR, preview.rows().get(2).action());
        assertTrue(preview.rows().get(2).detail().contains("Unrecognized organization type"));

        assertEquals(InitiativeImport.Action.ERROR, preview.rows().get(3).action());
        assertEquals("Duplicate email in this file.", preview.rows().get(3).detail());
        assertEquals(3, preview.errorCount());
        assertEquals(1, preview.memberCount());
    }

    @Test
    void previewDoesNotRequireTheColumnsToBePresentUntilTheyAreMapped() {
        InitiativeImport.ParsedSheet sheet = InitiativeImport.parseCsv(
                "Point of Contact,Email,Organization,Organization Type,Notes\nAda,ada@example.org,HLN,Consultant,Note\n");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> InitiativeImport.preview(sheet, new InitiativeImport.ColumnMapping(-1, -1, -1, -1, -1),
                        List.of(), List.of()));
        assertEquals("Map a contact, email, or organization column.", error.getMessage());
    }
}
