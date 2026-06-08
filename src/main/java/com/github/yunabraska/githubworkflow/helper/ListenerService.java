package com.github.yunabraska.githubworkflow.helper;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

/**
 * Owns disposable listener registrations that must follow IDE application or project lifetimes.
 */
@Service
public class ListenerService implements Disposable {
    /**
     * Disposes listener-owned resources.
     */
    @Override
    public void dispose() {
    }

    /**
     * Returns the application-level listener service.
     *
     * @return application listener service
     */
    @SuppressWarnings("unused")
    public static ListenerService getInstance() {
        return ApplicationManager.getApplication().getService(ListenerService.class);
    }

    /**
     * Returns the project-level listener service.
     *
     * @param project project whose listeners should be owned
     * @return project listener service
     */
    public static ListenerService getInstance(final Project project) {
        return project.getService(ListenerService.class);
    }
}
