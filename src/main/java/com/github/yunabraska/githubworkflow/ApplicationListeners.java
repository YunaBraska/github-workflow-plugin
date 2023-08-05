package com.github.yunabraska.githubworkflow;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;


public class ApplicationListeners implements ProjectManagerListener {

    private final Disposable disposable = Disposer.newDisposable();

    @Override
    public void projectOpened(@NotNull final Project project) {
        //TODO: On Focus Listener
        EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new FileChangeListener(project), disposable);
        project.getMessageBus().connect(disposable).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileOpenListener());
    }

    @Override
    public void projectClosed(@NotNull final Project project) {
        Disposer.dispose(disposable);
    }
}
