package com.github.yunabraska.githubworkflow.helper;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.GithubApiRequest;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubApiResponse;
import org.jetbrains.plugins.github.authentication.GHAccountsUtil;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount;
import org.jetbrains.plugins.github.util.GHCompatibilityUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Future;

import static java.util.Optional.ofNullable;

public class FileDownloader {

    private static final Logger LOG = Logger.getInstance(GitHubWorkflowHelper.class);

    private FileDownloader() {
        // static helper class
    }

    public static String downloadFileFromGitHub(final String downloadUrl) {
        return GHAccountsUtil.getAccounts().stream()
                .map(account -> downloadFromGitHub(downloadUrl, account))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }


    @SuppressWarnings({"java:S2142"})
    public static String downloadContent(final String urlString) {
        LOG.info("Download [" + urlString + "]");
        try {
            final ApplicationInfo applicationInfo = ApplicationInfo.getInstance();
            final Future<String> future = ApplicationManager.getApplication().executeOnPooledThread(() -> downloadSync(urlString, applicationInfo.getBuild().getProductCode() + "/" + applicationInfo.getFullVersion()));
            return future.get();
        } catch (final Exception e) {
            LOG.warn("Execution failed for [" + urlString + "] message [" + (e instanceof NullPointerException ? null : e.getMessage()) + "]");
        }
        return "";
    }

    //    @Nullable
//    public static String downloadSync(final String urlString, final String userAgent) {
//        try {
//            return HttpRequests
//                    .request(urlString)
//                    .gzip(true)
//                    .readTimeout(1000)
//                    .connectTimeout(1000)
//                    .userAgent(userAgent)
//                    .tuner(request -> request.setRequestProperty("Client-Name", "GitHub Workflow Plugin"))
//                    .readString();
//        } catch (final Exception e) {
//            return null;
//        }
//    }
    @Nullable
    public static String downloadSync(final String urlString, final String userAgent) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(urlString).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(1000); // Connect timeout
            connection.setReadTimeout(1000); // Read timeout
            connection.setRequestProperty("User-Agent", userAgent);
            connection.setRequestProperty("Client-Name", "GitHub Workflow Plugin");

            // Check for successful response code or throw error
            if (connection.getResponseCode() / 100 != 2) {
                throw new IOException("HTTP error code: " + connection.getResponseCode());
            }

            // Read response
            try (final BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                final StringBuilder response = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine).append(System.lineSeparator());
                }
                return response.toString();
            }
        } catch (final Exception e) {
            // Handle exceptions accordingly, returning null is often not a good practice
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String downloadFromGitHub(final String downloadUrl, final GithubAccount account) {
        return ofNullable(ProjectUtil.getActiveProject())
                .or(() -> Optional.of(ProjectManager.getInstance().getDefaultProject()))
                .map(project -> GHCompatibilityUtil.getOrRequestToken(account, project))
                .map(token -> downloadContent(downloadUrl, token))
                .orElse(null);
    }

    private static String downloadContent(final String downloadUrl, final String token) {
        try {
            return GithubApiRequestExecutor.Factory.getInstance().create(token).execute(new GithubApiRequest.Get<>(downloadUrl) {
                @Override
                public String extractResult(final @NotNull GithubApiResponse response) {
                    try {
                        return response.handleBody(inputStream -> {
                            try (final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
                                final StringBuilder stringBuilder = new StringBuilder();
                                String line;
                                while ((line = bufferedReader.readLine()) != null) {
                                    stringBuilder.append(line).append(System.lineSeparator());
                                }
                                return stringBuilder.toString();
                            }
                        });
                    } catch (final IOException ignored) {
                        return null;
                    }
                }
            });
        } catch (final Exception ignored) {
            return null;
        }
    }
}
