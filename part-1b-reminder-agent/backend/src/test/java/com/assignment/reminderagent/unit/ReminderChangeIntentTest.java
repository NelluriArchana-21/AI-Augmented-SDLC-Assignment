package com.assignment.reminderagent.unit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.assignment.reminderagent.model.ActionProposal;
import com.assignment.reminderagent.model.ReminderIntent;
import com.assignment.reminderagent.model.ReminderRequest;
import com.assignment.reminderagent.prompt.ReminderPromptBuilder;
import com.assignment.reminderagent.validation.ProposalValidationException;
import com.assignment.reminderagent.validation.ReminderProposalInvariantValidator;
import org.junit.jupiter.api.Test;

class ReminderChangeIntentTest {

  private final ReminderPromptBuilder promptBuilder = new ReminderPromptBuilder();
  private final ReminderProposalInvariantValidator invariantValidator =
      new ReminderProposalInvariantValidator();

  @Test
  void requiresAStatedRescheduleTargetAndCompleteNewLocalDateTime() {
    ReminderRequest request = request("Move my dentist appointment to January 22 at 11 AM.");

    assertDoesNotThrow(
        () ->
            invariantValidator.validate(
                new ActionProposal(
                    ReminderIntent.RESCHEDULE,
                    "dentist appointment",
                    "2026-01-22",
                    "11:00",
                    false,
                    null),
                request));
    assertInvalid(
        new ActionProposal(
            ReminderIntent.RESCHEDULE, null, "2026-01-22", "11:00", false, null),
        request);
    assertInvalid(
        new ActionProposal(
            ReminderIntent.RESCHEDULE,
            "dentist appointment",
            "2026-01-22",
            null,
            false,
            null),
        request);
  }

  @Test
  void requiresAnIdentifiedSnoozeTargetAndComputedDateTime() {
    ReminderRequest request = request("Snooze pay rent for 30 minutes.");

    assertDoesNotThrow(
        () ->
            invariantValidator.validate(
                new ActionProposal(
                    ReminderIntent.SNOOZE,
                    "pay rent",
                    "2026-01-15",
                    "10:30",
                    false,
                    null),
                request));
    assertInvalid(
        new ActionProposal(ReminderIntent.SNOOZE, null, "2026-01-15", "10:30", false, null),
        request);
    assertInvalid(
        new ActionProposal(ReminderIntent.SNOOZE, "pay rent", null, null, false, null),
        request);
  }

  @Test
  void rejectsUnstatedTargetsAndSnoozeTimesThatDoNotMatchTheStatedDelay() {
    ReminderRequest rescheduleRequest = request("Move my dentist appointment to January 22 at 11 AM.");
    assertInvalid(
        new ActionProposal(
            ReminderIntent.RESCHEDULE,
            "doctor appointment",
            "2026-01-22",
            "11:00",
            false,
            null),
        rescheduleRequest);

    ReminderRequest snoozeRequest = request("Snooze pay rent for 30 minutes.");
    assertInvalid(
        new ActionProposal(
            ReminderIntent.SNOOZE,
            "pay rent",
            "2026-01-15",
            "11:00",
            false,
            null),
        snoozeRequest);
    assertInvalid(
        new ActionProposal(
            ReminderIntent.SNOOZE,
            "pay rent",
            "2026-01-15",
            "10:30",
            false,
            null),
        request("Snooze pay rent."));
  }

  @Test
  void clarificationKeepsMissingRescheduleAndSnoozeValuesUnknown() {
    ReminderRequest request = request("Move my appointment to tomorrow at 9 AM.");
    ActionProposal ambiguousTarget =
        new ActionProposal(
            ReminderIntent.CLARIFY,
            null,
            "2026-01-16",
            "09:00",
            true,
            "Which appointment should I move?");
    ActionProposal missingDelay =
        new ActionProposal(
            ReminderIntent.CLARIFY,
            null,
            null,
            null,
            true,
            "How long should I snooze the reminder?");

    assertDoesNotThrow(() -> invariantValidator.validate(ambiguousTarget, request));
    assertDoesNotThrow(() -> invariantValidator.validate(missingDelay, request));
  }

  @Test
  void givesTheProviderExplicitRelativeDelayAndRescheduleRules() {
    ReminderRequest request =
        new ReminderRequest(
            "Snooze pay rent for 30 minutes.",
            "2026-01-15T10:00:00-05:00",
            "America/New_York");
    ReminderPromptBuilder.Prompt prompt = promptBuilder.build(request);
    String instructions = prompt.systemInstruction().replaceAll("\\s+", " ");

    assertTrue(instructions.contains("2026-01-15T10:00:00-05:00"));
    assertTrue(instructions.contains("America/New_York"));
    assertTrue(
        instructions.contains("add the stated delay to the trusted reference instant"));
    assertTrue(
        instructions.contains("a target that is missing or could identify more than one reminder"));
    assertEquals(request.naturalLanguageRequest(), prompt.userContent());
  }

  private void assertInvalid(ActionProposal proposal, ReminderRequest request) {
    assertThrows(
        ProposalValidationException.class,
        () -> invariantValidator.validate(proposal, request));
  }

  private ReminderRequest request(String text) {
    return new ReminderRequest(text, "2026-01-15T10:00:00-05:00", "America/New_York");
  }
}
