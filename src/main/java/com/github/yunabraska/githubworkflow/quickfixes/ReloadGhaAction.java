package com.github.yunabraska.githubworkflow.quickfixes;

import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import java.util.Objects;

import static com.github.yunabraska.githubworkflow.listeners.ApplicationStartup.asyncInitWorkflowFile;

public class ReloadGhaAction extends QuickFix {

    private final GitHubAction action;

    public ReloadGhaAction(final GitHubAction action, final Icon icon) {
        super(icon);
        this.action = action;
    }

    @NotNull
    @Override
    public String getText() {
        return "Reload [" + action.slug() + "]";
    }

    @NotNull
    @Override
    public String getFamilyName() {
        return "ReloadGhaAction";
    }

    @Override
    public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
        return true;
    }

    @Override
    public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
        action.deleteCache();
        asyncInitWorkflowFile(project, file.getVirtualFile());
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        final ReloadGhaAction that = (ReloadGhaAction) o;
        return Objects.equals(action, that.action);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), action);
    }
}
