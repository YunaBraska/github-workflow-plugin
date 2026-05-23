package com.github.yunabraska.githubworkflow.services;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

/**
 * Persistent user settings for the GitHub Workflow plugin.
 */
@State(name = "GitHubWorkflowPluginSettings", storages = {@Storage("githubWorkflowPluginSettings.xml")})
public final class PluginSettings implements PersistentStateComponent<PluginSettings.StateData> {

    public static final String SYSTEM_LANGUAGE = "";

    public static final class StateData {
        public String languageTag = SYSTEM_LANGUAGE;
    }

    private final StateData state = new StateData();

    public static PluginSettings getInstance() {
        return ApplicationManager.getApplication().getService(PluginSettings.class);
    }

    public static Optional<PluginSettings> maybeInstance() {
        try {
            return Optional.ofNullable(ApplicationManager.getApplication())
                    .map(application -> application.getService(PluginSettings.class));
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

    public PluginSettings languageTag(final String languageTag) {
        state.languageTag = languageTag == null ? SYSTEM_LANGUAGE : languageTag.trim();
        return this;
    }

    public Optional<Locale> localeOverride() {
        final String languageTag = languageTag();
        return languageTag.isBlank() ? Optional.empty() : Optional.of(Locale.forLanguageTag(languageTag.replace('_', '-')));
    }
}
