package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ChecklistTemplateSqlTest {

    @Test
    void writesTheSeedBlock() throws Exception {
        String initiative = Files.readString(Path.of("docs/initiatives/templates/initiative-checklist-v1.md"));
        String smallGroup = Files.readString(Path.of("docs/initiatives/templates/small-group-checklist-v1.md"));
        String sql = ChecklistTemplateSql.generate(initiative, smallGroup);
        Path output = Path.of("target/checklist-template-seed.sql");
        Files.createDirectories(output.getParent());
        Files.writeString(output, sql);
        assertTrue(sql.contains("'initiative-purpose'"));
        assertTrue(sql.contains("'close-small-group'"));
        assertTrue(sql.contains("kind: INITIATIVE"));
        assertTrue(sql.contains("kind: SMALL_GROUP"));
    }
}
