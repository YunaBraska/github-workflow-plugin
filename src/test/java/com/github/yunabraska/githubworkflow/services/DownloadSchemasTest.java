package com.github.yunabraska.githubworkflow.services;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static com.github.yunabraska.githubworkflow.helper.FileDownloader.downloadSync;
import static junit.framework.TestCase.assertNotNull;

public class DownloadSchemasTest {

    @Test
    public void downloadSchemas() throws IOException {
        final List<String> schemaNames = List.of(
                "dependabot-2.0",
                "github-action",
                "github-funding",
                "github-workflow",
                "github-discussion",
                "github-issue-forms",
                "github-issue-config",
                "github-workflow-template-properties"
        );

        final Path directory = Path.of(System.getProperty("user.dir"), "src", "main", "resources", "schemas");
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            Files.createDirectories(directory);
        }

        for (final String schemaName : schemaNames) {
            final String schemaContent = downloadSync(
                    "https://json.schemastore.org/" + schemaName, "JetBrains GithubWorkflow");
            assertNotNull(schemaContent);
            Files.writeString(
                    Path.of(directory.toFile().getAbsolutePath(), schemaName + ".json"),
                    schemaContent,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            System.out.println("Saved " + schemaName);
            System.out.println("Failed to fetch " + schemaName);
        }
    }
}
