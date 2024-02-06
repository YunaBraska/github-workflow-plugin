package com.github.yunabraska.githubworkflow;

import java.util.Arrays;
import java.util.Optional;

public enum PropertyType {

    BOOLEAN, NUMBER, STRING;

    public static Optional<PropertyType> propertyTypeOf(final String input) {
        final String type = input == null ? "" : input.toUpperCase();
        return Arrays.stream(PropertyType.values()).filter(propertyType -> propertyType.name().equals(type)).findFirst();
    }
}
