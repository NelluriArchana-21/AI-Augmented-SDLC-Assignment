package com.assignment.smarttodo.validation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class TagNormalizer {

  private TagNormalizer() {}

  public static List<String> normalize(List<String> tags) {
    if (tags == null || tags.isEmpty()) {
      return new ArrayList<>();
    }

    Set<String> seen = new LinkedHashSet<>();
    List<String> normalized = new ArrayList<>();
    for (String tag : tags) {
      String normalizedTag = normalizeValue(tag);
      if (normalizedTag == null || normalizedTag.isEmpty() || seen.contains(normalizedTag)) {
        continue;
      }
      seen.add(normalizedTag);
      normalized.add(normalizedTag);
    }
    return normalized;
  }

  public static String normalizeValue(String tag) {
    if (tag == null) {
      return null;
    }
    String value = tag.trim().toLowerCase();
    if (value.isEmpty()) {
      return null;
    }
    return value;
  }
}
