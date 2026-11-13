package com.assignment.reminderagent.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assignment.reminderagent.ReminderAgentApplication;
import com.assignment.reminderagent.support.FakeProviderTransport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    classes = {ReminderAgentApplication.class, ReminderProposalApiTest.TransportConfiguration.class},
    properties = {
      "reminder-agent.provider.api-key=test-only-api-key",
      "reminder-agent.provider.model-id=test-only-model"
    })
@AutoConfigureMockMvc
class ReminderProposalApiTest {

  private static final String ENDPOINT = "/api/v1/reminder-proposals";
  private static final String CONTEXT =
      """
      "reference_datetime":"2026-01-15T10:00:00-05:00","timezone":"America/New_York"
      """;

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private FakeProviderTransport fakeTransport;

  @Test
  void validatesRequestsAndKeepsProviderFailuresSeparateFromClarifications() throws Exception {
    MvcResult createResult =
        mockMvc
            .perform(
                post(ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request("Remind me to call Maya tomorrow at 9 AM.")))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode create = objectMapper.readTree(createResult.getResponse().getContentAsString());
    assertEquals(
        Set.of(
            "intent",
            "task",
            "date",
            "time",
            "needs_clarification",
            "clarification_question"),
        keys(create));
    assertEquals("CREATE", create.path("intent").textValue());
    assertEquals("2026-01-16", create.path("date").textValue());
    assertEquals(1, fakeTransport.requests().size());

    JsonNode mistypedContext =
        response(
            mockMvc
                .perform(
                    post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            {"natural_language_request":"Remind me tomorrow at 9 AM.","reference_datetime":7,"timezone":"America/New_York"}
                            """))
                .andExpect(status().isOk())
                .andReturn());
    assertEquals("CLARIFY", mistypedContext.path("intent").textValue());
    assertEquals(1, fakeTransport.requests().size());

    JsonNode malformedRequest =
        response(
            mockMvc
                .perform(
                    post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isOk())
                .andReturn());
    assertEquals("CLARIFY", malformedRequest.path("intent").textValue());
    assertTrue(malformedRequest.path("task").isNull());
    assertTrue(malformedRequest.path("date").isNull());
    assertTrue(malformedRequest.path("time").isNull());
    assertEquals(1, fakeTransport.requests().size());

    JsonNode missingTime =
        response(
            mockMvc
                .perform(
                    post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("Remind me to email team tomorrow.")))
                .andExpect(status().isOk())
                .andReturn());
    assertEquals("CLARIFY", missingTime.path("intent").textValue());
    assertTrue(missingTime.path("time").isNull());
    assertEquals(2, fakeTransport.requests().size());

    MvcResult malformedProviderResult =
        mockMvc
            .perform(
                post(ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request("malformed output")))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.code").value("STRUCTURAL_FAILURE"))
            .andReturn();
    assertFalse(
        objectMapper
            .readTree(malformedProviderResult.getResponse().getContentAsString())
            .has("intent"));
    assertEquals(3, fakeTransport.requests().size());

    MvcResult invalidDateResult =
        mockMvc
            .perform(
                post(ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request("impossible date")))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.code").value("STRUCTURAL_FAILURE"))
            .andReturn();
    assertFalse(
        objectMapper
            .readTree(invalidDateResult.getResponse().getContentAsString())
            .has("intent"));
    assertEquals(4, fakeTransport.requests().size());

    MvcResult providerFailure =
        mockMvc
            .perform(
                post(ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request("provider failure")))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("OPERATIONAL_FAILURE"))
            .andReturn();
    String failureBody = providerFailure.getResponse().getContentAsString();
    assertFalse(failureBody.contains("test-only-api-key"));
    assertFalse(objectMapper.readTree(failureBody).has("intent"));
    assertEquals(5, fakeTransport.requests().size());
  }

  private JsonNode response(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private Set<String> keys(JsonNode node) {
    return StreamSupport.stream(
            java.util.Spliterators.spliteratorUnknownSize(node.fieldNames(), 0), false)
        .collect(java.util.stream.Collectors.toSet());
  }

  private String request(String text) {
    try {
      return "{\"natural_language_request\":"
          + objectMapper.writeValueAsString(text)
          + ","
          + CONTEXT
          + "}";
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to create test request", exception);
    }
  }

  @TestConfiguration
  static class TransportConfiguration {

    @Bean
    @Primary
    FakeProviderTransport fakeProviderTransport() {
      return new FakeProviderTransport(
          request -> {
            String body = request.body();
            if (body.contains("malformed output")) {
              return response("{");
            }
            if (body.contains("impossible date")) {
              return response(
                  """
                  {"intent":"CREATE","task":"Known task","date":"2026-02-30","time":"09:00","needs_clarification":false,"clarification_question":null}
                  """.strip());
            }
            if (body.contains("provider failure")) {
              return new FakeProviderTransport.Response(401, "test-only-api-key");
            }
            if (body.contains("email team tomorrow")) {
              return response(
                  """
                  {"intent":"CLARIFY","task":"Email team","date":"2026-01-16","time":null,"needs_clarification":true,"clarification_question":"What time?"}
                  """.strip());
            }
            if (body.contains("Move my dentist appointment reminder to January 22, 2026 at 11 AM")) {
              return response(
                  """
                  {"intent":"RESCHEDULE","task":"dentist appointment","date":"2026-01-22","time":"11:00","needs_clarification":false,"clarification_question":null}
                  """.strip());
            }
            if (body.contains("Move my dentist appointment reminder to January 22")) {
              return response(
                  """
                  {"intent":"CLARIFY","task":"dentist appointment","date":"2026-01-22","time":null,"needs_clarification":true,"clarification_question":"What time should I move it to?"}
                  """.strip());
            }
            if (body.contains("Move my appointment to tomorrow at 9 AM")) {
              return response(
                  """
                  {"intent":"CLARIFY","task":null,"date":"2026-01-16","time":"09:00","needs_clarification":true,"clarification_question":"Which appointment should I move?"}
                  """.strip());
            }
            if (body.contains("Snooze pay rent for 30 minutes")) {
              return response(
                  """
                  {"intent":"SNOOZE","task":"pay rent","date":"2026-01-15","time":"10:30","needs_clarification":false,"clarification_question":null}
                  """.strip());
            }
            if (body.contains("Snooze my medication reminder")) {
              return response(
                  """
                  {"intent":"CLARIFY","task":null,"date":null,"time":null,"needs_clarification":true,"clarification_question":"How long should I snooze the reminder?"}
                  """.strip());
            }
            return response(
                """
                {"intent":"CREATE","task":"Call Maya","date":"2026-01-16","time":"09:00","needs_clarification":false,"clarification_question":null}
                """.strip());
          });
    }

    private static FakeProviderTransport.Response response(String completion) {
      try {
        String text = new ObjectMapper().writeValueAsString(completion);
        return new FakeProviderTransport.Response(
            200,
            "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":"
                + text
                + "}]}}]}");
      } catch (JsonProcessingException exception) {
        throw new IllegalStateException("Unable to create fake provider response", exception);
      }
    }
  }
}
