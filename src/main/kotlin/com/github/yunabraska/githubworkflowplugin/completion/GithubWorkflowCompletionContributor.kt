package com.github.yunabraska.githubworkflowplugin.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.lang.Language
import com.intellij.patterns.PlatformPatterns

class GithubWorkflowCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withLanguage(Language.ANY),
            GithubWorkflowCompletionProvider()
        )
    }
}
