package com.github.yunabraska.githubworkflowplugin.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.util.ProcessingContext
import org.yaml.snakeyaml.Yaml
import java.io.StringReader

class GithubWorkflowCompletionProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
//        val psiFile = parameters.originalFile
//        val fileType: FileType = psiFile.virtualFile?.let { FileTypeManager.getInstance().getFileTypeByFile(it) }
//            ?: PlainTextFileType.INSTANCE

//        if (fileType.defaultExtension != "yml" && fileType.defaultExtension != "yaml") {
//            return
//        }

//        if (!psiFile.name.contains(".github/workflows/")) {
//            return
//        }

//        val yaml = Yaml()
//        val parsedContent = yaml.load<Map<String, Any>>(StringReader(psiFile.text))

        // Use the parsedContent to provide completion suggestions based on the YAML structure
        // Example: suggest keys or values based on the current position in the YAML file

        // Adding a simple example completion item
        val exampleCompletion = LookupElementBuilder.create("example_key")
        result.addElement(exampleCompletion)
    }
}

