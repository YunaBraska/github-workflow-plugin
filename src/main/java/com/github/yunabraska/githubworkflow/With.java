package com.github.yunabraska.githubworkflow;

import com.github.yunabraska.githubworkflow.helper.PsiElementHelper;
import org.jetbrains.yaml.psi.YAMLKeyValue;

public class With extends Parameter {

    public With(final YAMLKeyValue element) {
        super(element, kv -> PsiElementHelper.getText(kv).orElse(null));
    }
}
