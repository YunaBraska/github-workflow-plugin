package com.github.yunabraska.githubworkflow.services;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public final class GitHubWorkflowBundle {

    @NonNls
    private static final String BUNDLE = "messages.GitHubWorkflowBundle";
    private static final DynamicBundle INSTANCE = new DynamicBundle(GitHubWorkflowBundle.class, BUNDLE);

    public static String message(@PropertyKey(resourceBundle = BUNDLE) final String key, final Object... params) {
        final var locale = PluginSettings.maybeInstance().flatMap(PluginSettings::localeOverride);
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
}
