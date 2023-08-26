package com.github.yunabraska.githubworkflow.quickfixes;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.function.Consumer;

import static java.util.Optional.ofNullable;

public class OpenSettingsIntentionAction extends QuickFix {

    private final Consumer<Project> execute;

    public OpenSettingsIntentionAction(final Consumer<Project> execute, final Icon icon) {
        super(icon);
        this.execute = execute;
    }

    @NotNull
    @Override
    public String getText() {
        return "Add gitHub account";
    }

    @NotNull
    @Override
    public String getFamilyName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
        return true;
    }

    @Override
    public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, "GitHub");
        ofNullable(execute).ifPresent(projectConsumer -> projectConsumer.accept(project));
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }
}
