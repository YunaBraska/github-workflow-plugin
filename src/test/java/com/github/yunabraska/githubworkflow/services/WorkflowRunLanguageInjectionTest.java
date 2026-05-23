package com.github.yunabraska.githubworkflow.services;

import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.yaml.psi.YAMLScalar;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class WorkflowRunLanguageInjectionTest extends EditorFeatureTestCase {

    public void testRunBlockInjectsShellScriptLanguageWhenAvailable() {
        assertThat(Language.findLanguageByID("Shell Script")).isNotNull();

        configureWorkflowProjectFile("""
                name: Injection
                on: workflow_dispatch
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                      - shell: bash
                        run: |
                          echo "hello"
                          <caret>if [ -f pom.xml ]; then
                            echo ok
                          fi
                """);

        final YAMLScalar scalar = scalarAtCaret();
        final List<Pair<PsiElement, TextRange>> injected = InjectedLanguageManager.getInstance(getProject()).getInjectedPsiFiles(scalar);

        assertThat(injected).isNotEmpty();
        assertThat(injected.get(0).first.getLanguage().getID()).isEqualTo("Shell Script");
    }

    private YAMLScalar scalarAtCaret() {
        final int offset = myFixture.getCaretOffset();
        final YAMLScalar scalar = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), YAMLScalar.class)
                .stream()
                .filter(candidate -> candidate.getTextRange().getStartOffset() <= offset)
                .filter(candidate -> offset <= candidate.getTextRange().getEndOffset())
                .findFirst()
                .orElse(null);
        assertThat(scalar).isNotNull();
        return scalar;
    }
}
