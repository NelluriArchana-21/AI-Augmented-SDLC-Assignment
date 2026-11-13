package com.assignment.reminderagent.parsing;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Component;

@Component
public final class ModelOutputParser {

  private static final Set<String> CONTRACT_KEYS =
      Set.of(
          "intent",
          "task",
          "date",
          "time",
          "needs_clarification",
          "clarification_question");

  private final ObjectMapper objectMapper;

  public ModelOutputParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public JsonNode parse(String content) {
    if (content == null || content.isBlank()) {
      throw new MalformedModelOutputException();
    }
    try (JsonParser parser = objectMapper.getFactory().createParser(content)) {
      parser.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
      JsonNode result = objectMapper.readTree(parser);
      if (result == null || !result.isObject() || parser.nextToken() != null) {
        throw new MalformedModelOutputException();
      }
      Set<String> keys =
          StreamSupport.stream(
                  java.util.Spliterators.spliteratorUnknownSize(result.fieldNames(), 0), false)
              .collect(java.util.stream.Collectors.toSet());
      if (!keys.equals(CONTRACT_KEYS)) {
        throw new MalformedModelOutputException();
      }
      return result;
    } catch (IOException exception) {
      throw new MalformedModelOutputException();
    }
  }
}
