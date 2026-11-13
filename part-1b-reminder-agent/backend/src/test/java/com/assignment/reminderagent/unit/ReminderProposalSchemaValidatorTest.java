package com.assignment.reminderagent.unit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.assignment.reminderagent.validation.ProposalValidationException;
import com.assignment.reminderagent.validation.ReminderProposalSchemaValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReminderProposalSchemaValidatorTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private ReminderProposalSchemaValidator validator;
  private JsonNode validCreate;

  @BeforeEach
  void loadNormativeSchemaAndValidProposal() throws Exception {
    validator = new ReminderProposalSchemaValidator(objectMapper);
    validCreate =
        objectMapper.readTree(
            """
            {"intent":"CREATE","task":"Call Maya","date":"2026-01-16","time":"09:00","needs_clarification":false,"clarification_question":null}
            """);
  }

  @Test
  void acceptsEachSupportedIntentCombination() throws Exception {
    assertValid(validCreate);
    assertValid(
        objectMapper.readTree(
            """
            {"intent":"RESCHEDULE","task":"dentist appointment","date":"2026-01-22","time":"11:00","needs_clarification":false,"clarification_question":null}
            """));
    assertValid(
        objectMapper.readTree(
            """
            {"intent":"SNOOZE","task":"pay rent","date":"2026-01-15","time":"10:30","needs_clarification":false,"clarification_question":null}
            """));
    assertValid(
        objectMapper.readTree(
            """
            {"intent":"CLARIFY","task":null,"date":null,"time":null,"needs_clarification":true,"clarification_question":"What time?"}
            """));
  }

  @Test
  void rejectsMissingExtraAndWrongTypedFields() throws Exception {
    ObjectNode missing = ((ObjectNode) validCreate).deepCopy();
    missing.remove("time");
    assertInvalid(missing);

    ObjectNode extra = ((ObjectNode) validCreate).deepCopy();
    extra.put("unexpected", "value");
    assertInvalid(extra);

    ObjectNode wrongType = ((ObjectNode) validCreate).deepCopy();
    wrongType.put("needs_clarification", "false");
    assertInvalid(wrongType);
  }

  @Test
  void rejectsInvalidIntentCombinationsAndTaskQuestionAndTimeBoundaries() throws Exception {
    ObjectNode actionAsClarification = ((ObjectNode) validCreate).deepCopy();
    actionAsClarification.put("needs_clarification", true);
    assertInvalid(actionAsClarification);

    ObjectNode blankTask = ((ObjectNode) validCreate).deepCopy();
    blankTask.put("task", "   ");
    assertInvalid(blankTask);

    ObjectNode longTask = ((ObjectNode) validCreate).deepCopy();
    longTask.put("task", "x".repeat(121));
    assertInvalid(longTask);

    ObjectNode invalidTime = ((ObjectNode) validCreate).deepCopy();
    invalidTime.put("time", "9:00");
    assertInvalid(invalidTime);

    ObjectNode invalidDateFormat = ((ObjectNode) validCreate).deepCopy();
    invalidDateFormat.put("date", "not-a-date");
    assertInvalid(invalidDateFormat);

    ObjectNode invalidCalendarDate = ((ObjectNode) validCreate).deepCopy();
    invalidCalendarDate.put("date", "2026-02-30");
    assertInvalid(invalidCalendarDate);

    ObjectNode incompleteReschedule =
        (ObjectNode)
            objectMapper.readTree(
                """
                {"intent":"RESCHEDULE","task":null,"date":"2026-01-22","time":"11:00","needs_clarification":false,"clarification_question":null}
                """);
    assertInvalid(incompleteReschedule);

    ObjectNode incompleteSnooze =
        (ObjectNode)
            objectMapper.readTree(
                """
                {"intent":"SNOOZE","task":"pay rent","date":"2026-01-15","time":null,"needs_clarification":false,"clarification_question":null}
                """);
    assertInvalid(incompleteSnooze);

    JsonNode clarify =
        objectMapper.readTree(
            """
            {"intent":"CLARIFY","task":null,"date":null,"time":null,"needs_clarification":true,"clarification_question":"Which date?"}
            """);
    ObjectNode blankQuestion = ((ObjectNode) clarify).deepCopy();
    blankQuestion.put("clarification_question", " ");
    assertInvalid(blankQuestion);

    ObjectNode actionableClarify = ((ObjectNode) clarify).deepCopy();
    actionableClarify.put("needs_clarification", false);
    assertInvalid(actionableClarify);
  }

  private void assertValid(JsonNode value) {
    assertDoesNotThrow(() -> validator.validate(value), value.toString());
  }

  private void assertInvalid(JsonNode value) {
    assertThrows(ProposalValidationException.class, () -> validator.validate(value), value.toString());
  }
}
