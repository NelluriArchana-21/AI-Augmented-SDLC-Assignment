package com.assignment.reminderagent.provider;

import com.assignment.reminderagent.config.ReminderAgentProperties;
import com.assignment.reminderagent.provider.ProviderResult.FailureCategory;
import com.assignment.reminderagent.provider.ProviderResult.OperationalFailure;
import com.assignment.reminderagent.provider.ProviderResult.Success;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

@Component
public final class GeminiProviderAdapter implements ReminderExtractionProvider {

  private static final String API_BASE =
      "https://generativelanguage.googleapis.com/v1beta/models/";

  private final ReminderAgentProperties properties;
  private final ObjectMapper objectMapper;
  private final GeminiTransport transport;
  private final ProviderRetryPolicy retryPolicy;

  @Autowired
  public GeminiProviderAdapter(
      ReminderAgentProperties properties,
      ObjectMapper objectMapper,
      GeminiTransport transport) {
    this(properties, objectMapper, transport, new ProviderRetryPolicy());
  }

  public GeminiProviderAdapter(
      ReminderAgentProperties properties,
      ObjectMapper objectMapper,
      GeminiTransport transport,
      ProviderRetryPolicy retryPolicy) {
    this.properties = Objects.requireNonNull(properties, "properties");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    this.transport = Objects.requireNonNull(transport, "transport");
    this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
  }

  @Override
  public ProviderResult extract(String trustedInstructions, String untrustedRequest) {
    return extractWithAttempts(trustedInstructions, untrustedRequest).result();
  }

  @Override
  public Invocation extractWithAttempts(String trustedInstructions, String untrustedRequest) {
    String apiKey = properties.getApiKey();
    String modelId = properties.getModelId();
    if (apiKey == null || apiKey.isBlank() || modelId == null || modelId.isBlank()) {
      return new Invocation(new OperationalFailure(FailureCategory.CONFIGURATION), java.util.List.of());
    }

    URI uri =
        URI.create(
            API_BASE
                + UriUtils.encodePathSegment(modelId, StandardCharsets.UTF_8)
                + ":generateContent");
    String body = createRequestBody(trustedInstructions, untrustedRequest);
    Map<String, String> headers =
        Map.of(
            "Content-Type", "application/json",
            ReminderAgentProperties.API_KEY_HEADER, apiKey);

    ProviderRetryPolicy.ExecutionResult execution =
        retryPolicy.executeWithAttempts(timeout -> send(uri, headers, body, timeout));
    return new Invocation(execution.result(), execution.attempts());
  }

  private ProviderResult send(
      URI uri, Map<String, String> headers, String body, Duration timeout) {
    try {
      GeminiTransport.Response response = transport.send(uri, headers, body, timeout);
      int status = response.statusCode();
      if (status >= 200 && status < 300) {
        return new Success(extractCompletion(response.body()));
      }
      if (status == 401 || status == 403) {
        return new OperationalFailure(FailureCategory.AUTHENTICATION);
      }
      if (status == 408 || status == 504) {
        return new OperationalFailure(FailureCategory.TIMEOUT);
      }
      if (status == 429) {
        return new OperationalFailure(FailureCategory.RATE_LIMIT);
      }
      if (status >= 500) {
        return new OperationalFailure(FailureCategory.SERVER);
      }
      return new OperationalFailure(FailureCategory.CONFIGURATION);
    } catch (HttpTimeoutException exception) {
      return new OperationalFailure(FailureCategory.TIMEOUT);
    } catch (IOException exception) {
      return new OperationalFailure(FailureCategory.NETWORK);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return new OperationalFailure(FailureCategory.NETWORK);
    }
  }

  private String createRequestBody(String trustedInstructions, String untrustedRequest) {
    ObjectNode request = objectMapper.createObjectNode();
    ObjectNode systemInstruction = request.putObject("systemInstruction");
    systemInstruction.putArray("parts").addObject().put("text", trustedInstructions);

    ArrayNode contents = request.putArray("contents");
    ObjectNode userContent = contents.addObject().put("role", "user");
    userContent.putArray("parts").addObject().put("text", untrustedRequest);

    ObjectNode generationConfig = request.putObject("generationConfig");
    generationConfig.put("responseMimeType", "application/json");
    generationConfig.set("responseSchema", responseSchema());
    try {
      return objectMapper.writeValueAsString(request);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize provider request");
    }
  }

  private JsonNode responseSchema() {
    ObjectNode schema = objectMapper.createObjectNode().put("type", "OBJECT");
    ArrayNode required = schema.putArray("required");
    required.add("intent");
    required.add("task");
    required.add("date");
    required.add("time");
    required.add("needs_clarification");
    required.add("clarification_question");

    ObjectNode properties = schema.putObject("properties");
    properties
        .putObject("intent")
        .put("type", "STRING")
        .putArray("enum")
        .add("CREATE")
        .add("RESCHEDULE")
        .add("SNOOZE")
        .add("CLARIFY");
    properties.putObject("task").put("type", "STRING").put("nullable", true);
    properties.putObject("date").put("type", "STRING").put("nullable", true);
    properties.putObject("time").put("type", "STRING").put("nullable", true);
    properties.putObject("needs_clarification").put("type", "BOOLEAN");
    properties
        .putObject("clarification_question")
        .put("type", "STRING")
        .put("nullable", true);
    return schema;
  }

  private String extractCompletion(String responseBody) {
    if (responseBody == null) {
      return "";
    }
    JsonNode root;
    try {
      root = objectMapper.readTree(responseBody);
    } catch (IOException exception) {
      return responseBody;
    }
    JsonNode parts =
        root == null
            ? null
            : root.path("candidates").path(0).path("content").path("parts");
    if (parts == null || !parts.isArray() || parts.isEmpty()) {
      return responseBody;
    }
    StringBuilder completion = new StringBuilder();
    for (JsonNode part : parts) {
      JsonNode text = part.get("text");
      if (text == null || !text.isTextual()) {
        return responseBody;
      }
      completion.append(text.textValue());
    }
    return completion.toString();
  }
}
