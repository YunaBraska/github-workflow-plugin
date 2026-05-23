package com.github.yunabraska.githubworkflow.services;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.SubmittedReportInfo;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.net.URLEncoder;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Optional.ofNullable;

final class PluginErrorReportSubmitter extends ErrorReportSubmitter {

    @NonNls
    private static final String REPORT_URL = "https://github.com/YunaBraska/github-workflow-plugin/issues/new?labels=bug&template=---bug-report.md";

    @NotNull
    @Override
    public String getReportActionText() {
        return GitHubWorkflowBundle.message("error.report.action");
    }

    @Override
    public boolean submit(final IdeaLoggingEvent @NotNull [] events,
                          @Nullable final String additionalInfo,
                          @NotNull final Component parentComponent,
                          @NotNull final Consumer<? super SubmittedReportInfo> consumer) {
        if (events.length == 0) {
            consumer.consume(new SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.FAILED));
            return false;
        }

        final IdeaLoggingEvent event = events[0];
        final String throwableText = event.getThrowableText();

        final StringBuilder sb = new StringBuilder(REPORT_URL);

        sb.append(URLEncoder.encode(StringUtil.splitByLines(throwableText)[0], UTF_8));
        ofNullable(event.getThrowable())
                .map(Throwable::getMessage)
                .or(() -> Optional.of(throwableText).map(title -> StringUtil.splitByLines(title)[0]))
                .map(title -> "&title=" + URLEncoder.encode(title, UTF_8))
                .ifPresent(sb::append);

        sb.append("&body=");
        sb.append(URLEncoder.encode("\n\n### " + GitHubWorkflowBundle.message("error.report.description") + "\n", UTF_8));
        sb.append(URLEncoder.encode(StringUtil.defaultIfEmpty(additionalInfo, ""), UTF_8));

        sb.append(URLEncoder.encode("\n\n### " + GitHubWorkflowBundle.message("error.report.steps") + "\n", UTF_8));
        sb.append(URLEncoder.encode(GitHubWorkflowBundle.message("error.report.sample"), UTF_8));

        sb.append(URLEncoder.encode("\n\n### " + GitHubWorkflowBundle.message("error.report.message") + "\n", UTF_8));
        sb.append(URLEncoder.encode(StringUtil.defaultIfEmpty(event.getMessage(), ""), UTF_8));

        sb.append(URLEncoder.encode("\n\n### " + GitHubWorkflowBundle.message("error.report.runtime") + "\n", UTF_8));
        final PluginDescriptor descriptor = getPluginDescriptor();
        sb.append(URLEncoder.encode(GitHubWorkflowBundle.message("error.report.pluginVersion", descriptor.getVersion()) + "\n", UTF_8));
        final String ideInfo = ApplicationInfo.getInstance().getFullApplicationName() +
                " (" + ApplicationInfo.getInstance().getBuild().asString() + ")";
        sb.append(URLEncoder.encode(GitHubWorkflowBundle.message("error.report.ide", ideInfo) + "\n", UTF_8));
        sb.append(URLEncoder.encode(GitHubWorkflowBundle.message("error.report.os", SystemInfo.OS_NAME + " " + SystemInfo.OS_VERSION), UTF_8));

        sb.append(URLEncoder.encode("\n\n### " + GitHubWorkflowBundle.message("error.report.stacktrace") + "\n", UTF_8));
        sb.append(URLEncoder.encode("```\n", UTF_8));
        sb.append(URLEncoder.encode(throwableText, UTF_8));
        sb.append(URLEncoder.encode("```\n", UTF_8));

        BrowserUtil.browse(sb.toString());

        consumer.consume(new SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.NEW_ISSUE));
        return true;
    }
}
