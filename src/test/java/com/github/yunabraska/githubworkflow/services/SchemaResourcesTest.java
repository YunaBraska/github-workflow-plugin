package com.github.yunabraska.githubworkflow.services;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class SchemaResourcesTest {

    private static final List<String> SCHEMA_NAMES = List.of(
            "dependabot-2.0",
            "github-action",
            "github-funding",
            "github-workflow",
            "github-discussion",
            "github-issue-forms",
            "github-issue-config",
            "github-workflow-template-properties"
    );

    @Test
    public void packagedSchemasArePresentAndNonEmpty() throws IOException {
        final Path directory = Path.of(System.getProperty("user.dir"), "src", "main", "resources", "schemas");

        for (final String schemaName : SCHEMA_NAMES) {
            final Path schema = directory.resolve(schemaName + ".json");
            assertThat(schema).exists().isRegularFile();
            assertThat(Files.readString(schema))
                    .startsWith("{")
                    .contains("\"$schema\"")
                    .contains("\"$id\"");
        }
    }
}
