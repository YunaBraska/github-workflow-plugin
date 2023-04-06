package com.github.yunabraska.githubworkflow.completion;

import com.intellij.icons.AllIcons;

import javax.swing.*;

public enum NodeIcon {
    //https://jetbrains.design/intellij/resources/icons_list/
    ICON_NODE(AllIcons.Nodes.Interface),
    ICON_ENV(AllIcons.Nodes.Constant),
    ICON_METHOD(AllIcons.Nodes.Method),
    ICON_OUTPUT(AllIcons.Nodes.Field),
    ICON_INPUT(AllIcons.Nodes.Parameter),
    ICON_GITHUB_OUTPUT(AllIcons.Nodes.Variable),
    ICON_GITHUB_ENV(AllIcons.Nodes.Variable),
    ICON_STEP(AllIcons.Nodes.Class),
    ICON_JOB(AllIcons.Nodes.Class),
    ICON_SECRET(AllIcons.Nodes.Static),
    ;

    final Icon icon;

    NodeIcon(final Icon icon) {
        this.icon = icon;
    }

    public Icon icon() {
        return icon;
    }
}
