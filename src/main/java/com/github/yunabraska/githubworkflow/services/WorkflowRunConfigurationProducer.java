package com.github.yunabraska.githubworkflow.services;

import com.github.yunabraska.githubworkflow.helper.GitHubWorkflowHelper;
import com.github.yunabraska.githubworkflow.helper.PsiElementHelper;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.LazyRunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Creates GitHub Workflow run configurations from workflow YAML files.
 */
public final class WorkflowRunConfigurationProducer extends LazyRunConfigurationProducer<WorkflowRunConfiguration> {

    private static final WorkflowDispatchInputs DISPATCH_INPUTS = new WorkflowDispatchInputs();

    @Override
    public @NotNull ConfigurationFactory getConfigurationFactory() {
        return WorkflowRunConfigurationType.getInstance().factory();
    }

    @Override
    protected boolean setupConfigurationFromContext(
            @NotNull final WorkflowRunConfiguration configuration,
            @NotNull final ConfigurationContext context,
            @NotNull final Ref<PsiElement> sourceElement
    ) {
        final PsiFile file = workflowFile(context.getPsiLocation()).orElse(null);
        if (file == null) {
            return false;
        }
        final Project project = context.getProject();
        final WorkflowRepository repository = new WorkflowRepositoryResolver().resolve(project, file.getVirtualFile()).orElse(null);
        if (repository == null) {
            return false;
        }
        final String path = workflowPath(project, file.getVirtualFile()).orElse(file.getName());
        configuration.setName(GitHubWorkflowBundle.message("workflow.run.configuration.name", file.getName()));
        configuration.apiUrl(repository.apiUrl())
                .owner(repository.owner())
                .repo(repository.repo())
                .workflowPath(path)
                .ref(new WorkflowCurrentBranchResolver().resolve(project, file.getVirtualFile()).orElse("main"))
                .tokenEnvVar("")
                .inputsText(DISPATCH_INPUTS.defaultsText(file.getText()));
        sourceElement.set(file);
        return true;
    }

    @Override
    public boolean isConfigurationFromContext(
            @NotNull final WorkflowRunConfiguration configuration,
            @NotNull final ConfigurationContext context
    ) {
        return workflowFile(context.getPsiLocation())
                .flatMap(file -> workflowPath(context.getProject(), file.getVirtualFile()))
                .filter(path -> path.equals(configuration.workflowPath()))
                .filter(path -> new WorkflowCurrentBranchResolver().resolve(context.getProject())
                        .map(branch -> branch.equals(configuration.ref()))
                        .orElse(true))
                .isPresent();
    }

    private static Optional<PsiFile> workflowFile(final PsiElement element) {
        return Optional.ofNullable(element)
                .map(PsiElement::getContainingFile)
                .filter(file -> Optional.ofNullable(file.getVirtualFile())
                        .flatMap(PsiElementHelper::toPath)
                        .filter(GitHubWorkflowHelper::isWorkflowPath)
                        .isPresent());
    }

    static Optional<String> workflowPath(final Project project, final VirtualFile file) {
        return Optional.ofNullable(project)
                .flatMap(p -> Optional.ofNullable(com.intellij.openapi.project.ProjectUtil.guessProjectDir(p)))
                .map(VirtualFile::getPath)
                .map(Path::of)
                .flatMap(root -> Optional.ofNullable(file)
                        .map(VirtualFile::getPath)
                        .map(Path::of)
                        .map(root::relativize)
                        .map(Path::toString)
                        .map(path -> path.replace('\\', '/')));
    }
}
