package com.github.yunabraska.githubworkflow.services;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.SubmittedReportInfo;
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
        return "Report Exception";
    }

    @Override
    public boolean submit(final IdeaLoggingEvent @NotNull [] events,
                          @Nullable final String additionalInfo,
                          @NotNull final Component parentComponent,
                          @NotNull final Consumer<? super SubmittedReportInfo> consumer) {
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
        sb.append(URLEncoder.encode("\n\n### Description\n", UTF_8));
        sb.append(URLEncoder.encode(StringUtil.defaultIfEmpty(additionalInfo, ""), UTF_8));

        sb.append(URLEncoder.encode("\n\n### Steps to Reproduce\n", UTF_8));
        sb.append(URLEncoder.encode("Please provide code sample if applicable", UTF_8));

        sb.append(URLEncoder.encode("\n\n### Message\n", UTF_8));
        sb.append(URLEncoder.encode(StringUtil.defaultIfEmpty(event.getMessage(), ""), UTF_8));

        sb.append(URLEncoder.encode("\n\n### Runtime Information\n", UTF_8));
        final IdeaPluginDescriptor descriptor = PluginManagerCore.getPlugin(getPluginDescriptor().getPluginId());
        assert descriptor != null;
        sb.append(URLEncoder.encode("Plugin version : " + descriptor.getVersion() + "\n", UTF_8));
        sb.append(URLEncoder.encode("IDE: " + ApplicationInfo.getInstance().getFullApplicationName() +
                " (" + ApplicationInfo.getInstance().getBuild().asString() + ")\n", UTF_8));
        sb.append(URLEncoder.encode("OS: " + SystemInfo.getOsNameAndVersion(), UTF_8));

        sb.append(URLEncoder.encode("\n\n### Stacktrace\n", UTF_8));
        sb.append(URLEncoder.encode("```\n", UTF_8));
        sb.append(URLEncoder.encode(throwableText, UTF_8));
        sb.append(URLEncoder.encode("```\n", UTF_8));

        BrowserUtil.browse(sb.toString());

        consumer.consume(new SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.NEW_ISSUE));
        return true;
    }
}
