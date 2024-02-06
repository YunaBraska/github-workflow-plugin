package com.github.yunabraska.githubworkflow;

import com.github.yunabraska.githubworkflow.helper.PsiElementHelper;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.Objects;
import java.util.function.Function;

import static com.github.yunabraska.githubworkflow.PropertyType.STRING;

// https://docs.github.com/en/actions/reference/workflow-syntax-for-github-actions#onworkflow_callinputs
public class Parameter extends WorkflowObject{

    private final String id;
    private final String value;
    private final boolean required;
    private final PropertyType type;
    private final String description;
    private final String deprecationMessage;

    public Parameter(final TextRange range, final Function<YAMLKeyValue, String> value) {
        this(null, range, value);
    }
    public Parameter(final YAMLKeyValue element, final Function<YAMLKeyValue, String> value) {
        this(element, null, value);
    }

    public Parameter(final YAMLKeyValue element, final TextRange range, final Function<YAMLKeyValue, String> value) {
        super(element, range);
        this.id = element.getKeyText();
        this.type = PsiElementHelper.getChild(element, "type").flatMap(PsiElementHelper::getText).flatMap(PropertyType::propertyTypeOf).orElse(STRING);
        this.value = value == null? null : value.apply(element);
        this.required = PsiElementHelper.getChild(element, "required").flatMap(PsiElementHelper::getText).map(Boolean::valueOf).orElse(true);
        this.description = PsiElementHelper.getChild(element, "description").flatMap(PsiElementHelper::getText).orElse(null);
        this.deprecationMessage = PsiElementHelper.getChild(element, "deprecationMessage").flatMap(PsiElementHelper::getText).orElse(null);
    }

    public String id() {
        return id;
    }

    public PropertyType type() {
        return type;
    }

    public String value() {
        return value;
    }

    public boolean required() {
        return required;
    }

    public String description() {
        return description;
    }

    public String deprecationMessage() {
        return deprecationMessage;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Parameter parameter = (Parameter) o;
        return Objects.equals(id, parameter.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Parameter{" +
                "id='" + id + '\'' +
                ", value='" + value + '\'' +
                ", required=" + required +
                ", deprecated='" + (deprecationMessage != null) + '\'' +
                ", type=" + type +
                ", description='" + description + '\'' +
                '}';
    }
}
