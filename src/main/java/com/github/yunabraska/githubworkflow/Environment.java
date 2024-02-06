package com.github.yunabraska.githubworkflow;

import com.github.yunabraska.githubworkflow.helper.PsiElementHelper;
import org.jetbrains.yaml.psi.YAMLKeyValue;

// https://docs.github.com/en/actions/using-workflows/workflow-syntax-for-github-actions#jobsjob_idenvironment
public class Environment extends WorkflowObject{

    private final String name;
    private final String url;

    public Environment(final YAMLKeyValue element) {
        super(element);
        name = PsiElementHelper.getText(element, "name").orElse(null);
        url = PsiElementHelper.getText(element, "url").orElse(null);
    }

    public String name() {
        return name;
    }

    public String url() {
        return url;
    }
}
