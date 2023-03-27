package com.github.yunabraska.githubworkflowplugin.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Document;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.github.yunabraska.githubworkflowplugin.completion.GitHubWorkFlowUtils.getWorkflowFile;
import static com.github.yunabraska.githubworkflowplugin.completion.GitHubWorkflowCache.getActionInputs;
import static com.github.yunabraska.githubworkflowplugin.completion.GitHubWorkflowContext.getContextLookupElements;
import static com.github.yunabraska.githubworkflowplugin.completion.YamlNode.loadYaml;

/**
 * @author jansorg
 */
public class GitHubWorkflowCompletionContributor extends CompletionContributor {

    public GitHubWorkflowCompletionContributor() {
        System.out.println("GitHubWorkflowCompletionContributor initialized");
        // completions for plain text files
//        extend(CompletionType.BASIC, PlatformPatterns.psiElement(PlainTextTokenTypes.PLAIN_TEXT), completionProvider());

        // completions for content of string literals
//        extend(CompletionType.BASIC, PlatformPatterns.psiElement().with(new GitHubWorkFlowFilePattern()), completionProvider());

        // always suggest when invoked manually
//        extend(CompletionType.BASIC, PlatformPatterns.not(PlatformPatterns.alwaysFalse()), completionProvider());

        // all events?
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), completionProvider());
    }

    @NotNull
    private static CompletionProvider<CompletionParameters> completionProvider() {
        return new CompletionProvider<>() {

            public void addCompletions(@NotNull final CompletionParameters parameters,
                                       @NotNull final ProcessingContext context,
                                       @NotNull final CompletionResultSet resultSet) {
                System.out.println("GitHubWorkflowCompletionContributor called");
                getWorkflowFile(parameters.getPosition()).ifPresent(path -> {
                    //TODO: cache yamlNode in case of broken yaml format while editing
                    final YamlNode yamlNode = loadYaml(path);
                    final List<YamlNode> inputs = yamlNode.filter(node -> node.jobParent == null && "inputs".equalsIgnoreCase(node.name));
                    //TODO detect envs: echo "DYNAMIC_VARIABLE=some value" >> $GITHUB_ENV
                    //TODO if inside of ${{ }} or key=if then [inputs, github, env, steps, jobs, secrets]
                    final List<LookupElement> githubContextItems = getContextLookupElements(parameters.getOriginalFile().getText(), parameters.getOffset());
                    if (!githubContextItems.isEmpty()) {
                        //ADDS elements for env. inputs. github.
                        System.out.println("FOUND CONTEXT ITEMS [" + githubContextItems.size() + "]");
                        resultSet.addAllElements(githubContextItems);
                    } else {
                        //Adds elements for with
                        //TODO: autocompletion only on with: context
                        final String action = getAction(parameters.getPosition());
                        if (action != null) {
                            System.out.println("FOUND ACTION ITEMS action [" + action + "]");
                            getActionInputs(actionToUrl(action)).forEach((name, description) -> resultSet.addElement(LookupElementBuilder
                                    .create(name)
                                    .withIcon(AllIcons.General.Add)
                                    .withTypeText(description)
                                    .withInsertHandler((ctx, item) -> addDoubleDots(name, ctx))
                            ));
                        } else {
                            resultSet.addElement(LookupElementBuilder.create("TODO Element"));
                        }
                    }

                });


            }

            private void addDoubleDots(final String value, final InsertionContext ctx) {
                final int startOffset = ctx.getStartOffset();
                final int tailOffset = ctx.getTailOffset();
                int newOffset = startOffset + value.length();
                final Document document = ctx.getDocument();

                // Find the start of the identifier
                final CharSequence documentChars = document.getCharsSequence();
                // Find the end of the previous value
                int valueEnd = tailOffset;
                if (ctx.getCompletionChar() == '\t') {
                    while (valueEnd < documentChars.length() &&
                            documentChars.charAt(valueEnd) != ':' &&
                            documentChars.charAt(valueEnd) != '\n' &&
                            documentChars.charAt(valueEnd) != '\r') {
                        valueEnd++;
                    }
                    valueEnd++;
                }
                // Remove the previous value
                document.deleteString(startOffset, valueEnd);
                // Insert the new value
                document.insertString(startOffset, value);
                // Add ': ' after the inserted value
                final String toInsert = ": ";
                document.insertString(startOffset + value.length(), toInsert);
                newOffset += toInsert.length();
                // Update caret position
                ctx.getEditor().getCaretModel().moveToOffset(newOffset);
            }

            private String actionToUrl(final String action) {
                //TODO: with token Auth
                final int ref = action == null ? -1 : action.indexOf("@");
                return ref == -1 ? null : "https://raw.githubusercontent.com/"
                        + action.substring(0, ref)
                        + "/"
                        + action.substring(ref + 1)
                        + "/action.yml";
            }

            private String getAction(final PsiElement currentPosition) {
                final PsiElement parent = currentPosition.getParent();
                if (parent != null) {
                    if ("with".equals(parent.getFirstChild().getText())) {
                        final PsiElement action = parent.getParent();
                        final PsiElement[] children = action != null ? action.getChildren() : parent.getChildren();
                        for (int i = 0; i < children.length - 1; i++) {
                            if ("uses".equals(children[i].getFirstChild().getText())) {
                                return children[i].getLastChild().getText().trim();
                            }
                        }
                    }
                    return getAction(parent);
                }
                return null;
            }
        };
    }
}
