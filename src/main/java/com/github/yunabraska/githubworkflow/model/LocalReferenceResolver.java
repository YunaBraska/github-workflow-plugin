package com.github.yunabraska.githubworkflow.model;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.ResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getProject;
import static com.github.yunabraska.githubworkflow.services.ActionReferenceContributor.ACTION_KEY;
import static java.util.Optional.ofNullable;

public class LocalReferenceResolver extends PsiReferenceBase<PsiElement> implements PsiPolyVariantReference {

    public LocalReferenceResolver(@NotNull final PsiElement element) {
        super(element);
    }

    @Override
    public @NotNull ResolveResult @NotNull [] multiResolve(final boolean incompleteCode) {
        return ofNullable(myElement.getUserData(ACTION_KEY)).flatMap(action -> {
            final Project project = getProject(myElement);
            return ofNullable(project)
                    .flatMap(action::getLocalPath)
                    .map(path -> LocalFileSystem.getInstance().findFileByPath(path))
                    .map(virtualFile -> PsiManager.getInstance(project).findFile(virtualFile))
                    .map(target -> target.getChildren().length > 0 ? target.getChildren()[0] : target)
                    .map(target -> new ResolveResult[]{(new PsiElementResolveResult(target))});
        }).orElse(ResolveResult.EMPTY_ARRAY);
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        final ResolveResult[] resolveResults = multiResolve(false);
        return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
    }

    @Override
    public @NotNull String getCanonicalText() {
        return myElement.getText();
    }
}
