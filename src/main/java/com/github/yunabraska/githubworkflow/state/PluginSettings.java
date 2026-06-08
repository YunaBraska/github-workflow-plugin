package com.github.yunabraska.githubworkflow.state;

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
public class PluginSettings implements PersistentStateComponent<PluginSettings.StateData> {

    public static final String SYSTEM_LANGUAGE = "";

    /**
     * Serialized settings state stored by the IntelliJ platform.
     */
    public static class StateData {
        public String languageTag = SYSTEM_LANGUAGE;
    }

    private final StateData state = new StateData();

    /**
     * Returns the application-wide plugin settings service.
     *
     * @return plugin settings service managed by the IDE
     */
    public static PluginSettings getInstance() {
        return ApplicationManager.getApplication().getService(PluginSettings.class);
    }

    /**
     * Returns the settings service when the application container is ready.
     *
     * @return optional settings service, empty during early startup or isolated tests
     */
    public static Optional<PluginSettings> maybeInstance() {
        try {
            return Optional.ofNullable(ApplicationManager.getApplication())
                    .map(application -> application.getService(PluginSettings.class));
        } catch (final RuntimeException ignored) {
            return Optional.empty();
        }
    }

    @Override
    /**
     * Returns the current serialized settings state.
     *
     * @return current state object for IDE persistence
     */
    public @Nullable StateData getState() {
        return state;
    }

    /**
     * Replaces settings from persisted IDE state.
     *
     * @param state persisted settings state supplied by the IDE
     */
    @Override
    public void loadState(@NotNull final StateData state) {
        XmlSerializerUtil.copyBean(state, this.state);
    }

    /**
     * Returns the configured language tag.
     *
     * @return BCP 47 language tag, or an empty string for system language
     */
    public String languageTag() {
        return state.languageTag == null ? SYSTEM_LANGUAGE : state.languageTag;
    }

    /**
     * Updates the configured language tag.
     *
     * @param languageTag BCP 47 language tag, or blank/null to follow the IDE/system language
     * @return this settings service for fluent updates
     */
    public PluginSettings languageTag(final String languageTag) {
        state.languageTag = languageTag == null ? SYSTEM_LANGUAGE : languageTag.trim();
        return this;
    }

    /**
     * Returns the explicit locale override configured by the user.
     *
     * @return locale override, or empty when system language should be used
     */
    public Optional<Locale> localeOverride() {
        final String languageTag = languageTag();
        return languageTag.isBlank() ? Optional.empty() : Optional.of(Locale.forLanguageTag(languageTag.replace('_', '-')));
    }
}
