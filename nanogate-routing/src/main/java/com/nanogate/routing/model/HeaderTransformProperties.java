package com.nanogate.routing.model;

import java.util.List;
import java.util.Map;

/**
 * Represents a set of header transformations to be applied.
 *
 * @param add A map of header names and values to add or overwrite.
 * @param remove A list of header names to remove.
 */
public record HeaderTransformProperties(
        Map<String, String> add,
        List<String> remove
) {
}
