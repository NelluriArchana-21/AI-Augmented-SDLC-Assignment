package com.assignment.reminderagent.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assignment.reminderagent.ReminderAgentApplication;
import com.assignment.reminderagent.support.FakeProviderTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class ReminderChangeApiTest {

  private static final String ENDPOINT = "/api/v1/reminder-proposals";
  private static final String CONTEXT =
      """
      "reference_datetime":"2026-01-15T10:00:00-05:00","timezone":"America/New_York"
      """;

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private FakeProviderTransport fakeTransport;

  @Test
  void returnsRescheduleAndSnoozeProposalsOrClarifiesMissingInformation() throws Exception {
    int initialRequests = fakeTransport.requests().size();

    JsonNode reschedule =
        postProposal("Move my dentist appointment reminder to January 22, 2026 at 11 AM.");
    assertProposalKeys(reschedule);
    assertEquals("RESCHEDULE", reschedule.path("intent").textValue());
    assertEquals("dentist appointment", reschedule.path("task").textValue());
    assertEquals("2026-01-22", reschedule.path("date").textValue());
    assertEquals("11:00", reschedule.path("time").textValue());
    assertEquals(initialRequests + 1, fakeTransport.requests().size());

    JsonNode missingRescheduleTime =
        postProposal("Move my dentist appointment reminder to January 22.");
    assertProposalKeys(missingRescheduleTime);
    assertEquals("CLARIFY", missingRescheduleTime.path("intent").textValue());
    assertEquals("dentist appointment", missingRescheduleTime.path("task").textValue());
    assertTrue(missingRescheduleTime.path("time").isNull());

    JsonNode ambiguousTarget = postProposal("Move my appointment to tomorrow at 9 AM.");
    assertProposalKeys(ambiguousTarget);
    assertEquals("CLARIFY", ambiguousTarget.path("intent").textValue());
    assertTrue(ambiguousTarget.path("task").isNull());

    JsonNode snooze = postProposal("Snooze pay rent for 30 minutes.");
    assertProposalKeys(snooze);
    assertEquals("SNOOZE", snooze.path("intent").textValue());
    assertEquals("pay rent", snooze.path("task").textValue());
    assertEquals("2026-01-15", snooze.path("date").textValue());
    assertEquals("10:30", snooze.path("time").textValue());

    JsonNode missingDelay = postProposal("Snooze my medication reminder.");
    assertProposalKeys(missingDelay);
    assertEquals("CLARIFY", missingDelay.path("intent").textValue());
    assertTrue(missingDelay.path("date").isNull());
    assertTrue(missingDelay.path("time").isNull());
    assertEquals(initialRequests + 5, fakeTransport.requests().size());
  }

  @Test
  void keepsProviderFailuresOperationalForChangeIntents() throws Exception {
    int initialRequests = fakeTransport.requests().size();
    MvcResult result =
        mockMvc
            .perform(
                post(ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request("Snooze pay rent for 30 minutes; provider failure.")))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("OPERATIONAL_FAILURE"))
            .andReturn();

    assertFalse(objectMapper.readTree(result.getResponse().getContentAsString()).has("intent"));
    assertEquals(initialRequests + 1, fakeTransport.requests().size());
  }

  private JsonNode postProposal(String text) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post(ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request(text)))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private void assertProposalKeys(JsonNode proposal) {
    assertEquals(
        Set.of(
            "intent",
            "task",
            "date",
            "time",
            "needs_clarification",
            "clarification_question"),
        StreamSupport.stream(
                java.util.Spliterators.spliteratorUnknownSize(proposal.fieldNames(), 0), false)
            .collect(java.util.stream.Collectors.toSet()));
  }

  private String request(String text) throws Exception {
    return "{\"natural_language_request\":"
        + objectMapper.writeValueAsString(text)
        + ","
        + CONTEXT
        + "}";
  }
}
