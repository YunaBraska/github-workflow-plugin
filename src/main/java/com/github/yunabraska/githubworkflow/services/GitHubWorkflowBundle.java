package com.github.yunabraska.githubworkflow.services;

import com.intellij.DynamicBundle;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.SubmittedReportInfo;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.awt.Component;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.ResourceBundle;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Optional.ofNullable;

public final class GitHubWorkflowBundle {

    @NonNls
    private static final String BUNDLE = "messages.GitHubWorkflowBundle";
    private static final DynamicBundle INSTANCE = new DynamicBundle(GitHubWorkflowBundle.class, BUNDLE);

    public static String message(@PropertyKey(resourceBundle = BUNDLE) final String key, final Object... params) {
        final var locale = Settings.maybeInstance().flatMap(Settings::localeOverride);
        if (locale.isPresent()) {
            return messageFor(locale.get(), key, params);
        }
        return INSTANCE.getMessage(key, params);
    }

    static String messageFor(final Locale locale, final @PropertyKey(resourceBundle = BUNDLE) String key, final Object... params) {
        try {
            final ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE, locale);
            final String pattern = bundle.getString(key);
            return new MessageFormat(pattern, locale).format(params);
        } catch (final MissingResourceException ignored) {
            return INSTANCE.getMessage(key, params);
        }
    }

    private GitHubWorkflowBundle() {
        // static bundle
    }

    /**
     * Persistent user settings for the GitHub Workflow plugin.
     */
    @State(name = "GitHubWorkflowPluginSettings", storages = {@Storage("githubWorkflowPluginSettings.xml")})
    public static final class Settings implements PersistentStateComponent<Settings.StateData> {

        public static final String SYSTEM_LANGUAGE = "";

        public static final class StateData {
            public String languageTag = SYSTEM_LANGUAGE;
        }

        private final StateData state = new StateData();

        public static Settings getInstance() {
            return ApplicationManager.getApplication().getService(Settings.class);
        }

        public static Optional<Settings> maybeInstance() {
            try {
                return Optional.ofNullable(ApplicationManager.getApplication())
                        .map(application -> application.getService(Settings.class));
            } catch (final RuntimeException ignored) {
                return Optional.empty();
            }
        }

        @Override
        public @Nullable StateData getState() {
            return state;
        }

        @Override
        public void loadState(@NotNull final StateData state) {
            XmlSerializerUtil.copyBean(state, this.state);
        }

        public String languageTag() {
            return state.languageTag == null ? SYSTEM_LANGUAGE : state.languageTag;
        }

        public Settings languageTag(final String languageTag) {
            state.languageTag = languageTag == null ? SYSTEM_LANGUAGE : languageTag.trim();
            return this;
        }

        public Optional<Locale> localeOverride() {
            final String languageTag = languageTag();
            return languageTag.isBlank() ? Optional.empty() : Optional.of(Locale.forLanguageTag(languageTag.replace('_', '-')));
        }
    }

    public static final class ErrorReporter extends ErrorReportSubmitter {

        @NonNls
        private static final String REPORT_URL = "https://github.com/YunaBraska/github-workflow-plugin/issues/new?labels=bug&template=---bug-report.md";

        @NotNull
        @Override
        public String getReportActionText() {
            return message("error.report.action");
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
            final StringBuilder url = new StringBuilder(REPORT_URL);

            url.append(URLEncoder.encode(StringUtil.splitByLines(throwableText)[0], UTF_8));
            ofNullable(event.getThrowable())
                    .map(Throwable::getMessage)
                    .or(() -> Optional.of(throwableText).map(title -> StringUtil.splitByLines(title)[0]))
                    .map(title -> "&title=" + URLEncoder.encode(title, UTF_8))
                    .ifPresent(url::append);

            url.append("&body=");
            url.append(URLEncoder.encode("\n\n### " + message("error.report.description") + "\n", UTF_8));
            url.append(URLEncoder.encode(StringUtil.defaultIfEmpty(additionalInfo, ""), UTF_8));

            url.append(URLEncoder.encode("\n\n### " + message("error.report.steps") + "\n", UTF_8));
            url.append(URLEncoder.encode(message("error.report.sample"), UTF_8));

            url.append(URLEncoder.encode("\n\n### " + message("error.report.message") + "\n", UTF_8));
            url.append(URLEncoder.encode(StringUtil.defaultIfEmpty(event.getMessage(), ""), UTF_8));

            url.append(URLEncoder.encode("\n\n### " + message("error.report.runtime") + "\n", UTF_8));
            final PluginDescriptor descriptor = getPluginDescriptor();
            url.append(URLEncoder.encode(message("error.report.pluginVersion", descriptor.getVersion()) + "\n", UTF_8));
            final String ideInfo = ApplicationInfo.getInstance().getFullApplicationName() +
                    " (" + ApplicationInfo.getInstance().getBuild().asString() + ")";
            url.append(URLEncoder.encode(message("error.report.ide", ideInfo) + "\n", UTF_8));
            url.append(URLEncoder.encode(message("error.report.os", SystemInfo.OS_NAME + " " + SystemInfo.OS_VERSION), UTF_8));

            url.append(URLEncoder.encode("\n\n### " + message("error.report.stacktrace") + "\n", UTF_8));
            url.append(URLEncoder.encode("```\n", UTF_8));
            url.append(URLEncoder.encode(throwableText, UTF_8));
            url.append(URLEncoder.encode("```\n", UTF_8));

            BrowserUtil.browse(url.toString());
            consumer.consume(new SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.NEW_ISSUE));
            return true;
        }
    }
}
