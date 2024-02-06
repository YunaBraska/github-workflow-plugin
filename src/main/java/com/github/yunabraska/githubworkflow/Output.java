package com.github.yunabraska.githubworkflow;

import com.github.yunabraska.githubworkflow.helper.PsiElementHelper;
import org.jetbrains.yaml.psi.YAMLKeyValue;

public class Output extends Parameter {

    public Output(final YAMLKeyValue element) {
        super(element, kv -> PsiElementHelper.getText(kv).orElse(null));
    }
}
