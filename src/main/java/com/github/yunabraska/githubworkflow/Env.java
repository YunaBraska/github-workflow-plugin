package com.github.yunabraska.githubworkflow;

import com.github.yunabraska.githubworkflow.helper.PsiElementHelper;
import org.jetbrains.yaml.psi.YAMLKeyValue;

public class Env extends Parameter {

    public Env(final YAMLKeyValue element) {
        super(element, kv -> PsiElementHelper.getText(kv).orElse(null));
    }
}
