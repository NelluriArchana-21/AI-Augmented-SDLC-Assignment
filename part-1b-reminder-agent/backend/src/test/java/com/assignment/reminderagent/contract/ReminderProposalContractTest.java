package com.assignment.reminderagent.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.assignment.reminderagent.model.ActionProposal;
import com.assignment.reminderagent.model.ReminderIntent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

@JsonTest
class ReminderProposalContractTest {

  private static final Set<String> CONTRACT_KEYS =
      Set.of(
          "intent",
          "task",
          "date",
          "time",
          "needs_clarification",
          "clarification_question");

  @Autowired private ObjectMapper objectMapper;

  @Test
  void serializesEachActionableIntentWithTheExactSixTypedFields() throws Exception {
    for (ReminderIntent intent :
        Set.of(ReminderIntent.CREATE, ReminderIntent.RESCHEDULE, ReminderIntent.SNOOZE)) {
      ActionProposal proposal =
          new ActionProposal(intent, "Pay rent", "2026-01-16", "09:00", false, null);

      JsonNode json = serialize(proposal);

      assertEquals(CONTRACT_KEYS, keys(json));
      assertTrue(json.get("intent").isTextual());
      assertEquals(intent.name(), json.get("intent").textValue());
      assertTrue(json.get("task").isTextual());
      assertTrue(json.get("date").isTextual());
      assertTrue(json.get("time").isTextual());
      assertTrue(json.get("needs_clarification").isBoolean());
      assertFalse(json.get("needs_clarification").booleanValue());
      assertTrue(json.get("clarification_question").isNull());
    }
  }

  @Test
  void serializesClarificationWithNullableUnknownValuesAndNoExtraFields() throws Exception {
    ActionProposal proposal =
        new ActionProposal(ReminderIntent.CLARIFY, "Pay rent", null, null, true, "Which date?");

    JsonNode json = serialize(proposal);

    assertEquals(CONTRACT_KEYS, keys(json));
    assertEquals("CLARIFY", json.get("intent").textValue());
    assertTrue(json.get("task").isTextual());
    assertTrue(json.get("date").isNull());
    assertTrue(json.get("time").isNull());
    assertTrue(json.get("needs_clarification").isBoolean());
    assertTrue(json.get("needs_clarification").booleanValue());
    assertEquals("Which date?", json.get("clarification_question").textValue());
  }

  @Test
  void exposesExactlyTheFourSupportedIntentValues() {
    assertEquals(
        Set.of("CREATE", "RESCHEDULE", "SNOOZE", "CLARIFY"),
        Arrays.stream(ReminderIntent.values())
            .map(ReminderIntent::name)
            .collect(Collectors.toSet()));
  }

  private JsonNode serialize(ActionProposal proposal) throws Exception {
    return objectMapper.readTree(objectMapper.writeValueAsString(proposal));
  }

  private Set<String> keys(JsonNode json) {
    return StreamSupport.stream(
            java.util.Spliterators.spliteratorUnknownSize(json.fieldNames(), 0), false)
        .collect(Collectors.toSet());
  }
}
