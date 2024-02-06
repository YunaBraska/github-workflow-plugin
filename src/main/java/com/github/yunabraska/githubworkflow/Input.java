package com.github.yunabraska.githubworkflow;

import com.github.yunabraska.githubworkflow.helper.PsiElementHelper;
import org.jetbrains.yaml.psi.YAMLKeyValue;

public class Input extends Parameter {

    public Input(final YAMLKeyValue element) {
        super(element, kv -> PsiElementHelper.getChild(element, "default").flatMap(PsiElementHelper::getText).orElse(null));
    }
}
