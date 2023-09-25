package com.github.yunabraska.githubworkflow.helper;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.github.api.GithubApiRequest;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubApiResponse;
import org.jetbrains.plugins.github.authentication.GHAccountsUtil;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount;
import org.jetbrains.plugins.github.util.GHCompatibilityUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
            final Future<String> future = ApplicationManager.getApplication().executeOnPooledThread(() -> {
                try {
                    return HttpRequests
                            .request(urlString)
                            .gzip(true)
                            .readTimeout(5000)
                            .connectTimeout(5000)
                            .userAgent(applicationInfo.getBuild().getProductCode() + "/" + applicationInfo.getFullVersion())
                            .tuner(request -> request.setRequestProperty("Client-Name", "GitHub Workflow Plugin"))
                            .readString();
                } catch (final Exception e) {
                    return null;
                }
            });
            return future.get();
        } catch (final Exception e) {
            LOG.warn("Execution failed for [" + urlString + "] message [" + (e instanceof NullPointerException ? null : e.getMessage()) + "]");
        }
        return "";
    }

    //    @SuppressWarnings("DataFlowIssue")
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
