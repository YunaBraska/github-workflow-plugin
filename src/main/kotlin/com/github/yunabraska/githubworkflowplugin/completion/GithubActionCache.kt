package com.github.yunabraska.githubworkflowplugin.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import java.time.Duration
import java.time.LocalDateTime

class GithubActionCache private constructor() {
    private val cacheDuration = Duration.ofDays(1)
    private var lastUpdated: LocalDateTime = LocalDateTime.MIN
    private var completions: List<LookupElement> = emptyList()

    companion object {
        val instance: GithubActionCache by lazy { GithubActionCache() }
    }

    fun getCompletions(): List<LookupElement> {
        if (isCacheExpired()) {
            updateCache()
        }
        return completions
    }

    private fun isCacheExpired(): Boolean {
        return Duration.between(lastUpdated, LocalDateTime.now()) > cacheDuration
    }

    private fun updateCache() {
        // Fetch the action.yml file from the repository and parse it to generate completions
        // ...

        // For the sake of this example, let's assume that we have fetched and parsed the action.yml file and created a list of completion items
        val fetchedCompletions = listOf(
            LookupElementBuilder.create("on"),
            LookupElementBuilder.create("jobs"),
            LookupElementBuilder.create("steps"),
            // ...
        )

        completions = fetchedCompletions
        lastUpdated = LocalDateTime.now()
    }
}
