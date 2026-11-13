package com.assignment.reminderagent.unit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.assignment.reminderagent.model.ActionProposal;
import com.assignment.reminderagent.model.ReminderIntent;
import com.assignment.reminderagent.model.ReminderRequest;
import com.assignment.reminderagent.validation.ProposalValidationException;
import com.assignment.reminderagent.validation.ReminderProposalInvariantValidator;
import org.junit.jupiter.api.Test;

class ReminderProposalInvariantValidatorTest {

  private final ReminderProposalInvariantValidator validator =
      new ReminderProposalInvariantValidator();

  @Test
  void acceptsContractValidProposalsForAllFourIntents() {
    for (ReminderIntent intent :
        new ReminderIntent[] {
          ReminderIntent.CREATE, ReminderIntent.RESCHEDULE, ReminderIntent.SNOOZE
        }) {
      ReminderRequest request =
          request(
              switch (intent) {
                case CREATE -> "Remind me about Known task on January 16 at 9 AM.";
                case RESCHEDULE -> "Move Known task to January 16 at 9 AM.";
                case SNOOZE -> "Snooze Known task for 23 hours.";
                case CLARIFY -> throw new IllegalStateException("Unexpected intent");
              },
              "2026-01-15T10:00:00-05:00",
              "America/New_York");
      assertDoesNotThrow(
          () ->
              validator.validate(
                  new ActionProposal(intent, "Known task", "2026-01-16", "09:00", false, null),
                  request));
    }

    assertDoesNotThrow(
        () ->
            validator.validate(
                new ActionProposal(ReminderIntent.CLARIFY, null, null, null, true, "What time?"),
                request("Remind me about a known task.", "2026-01-15T10:00:00-05:00", "America/New_York")));
  }

  @Test
  void rejectsImpossibleDatesIncompleteActionsAndInvalidLocalTimes() {
    ReminderRequest request = request("2026-01-15T10:00:00-05:00", "America/New_York");

    assertInvalid(
        new ActionProposal(
            ReminderIntent.CREATE, "Known task", "2026-02-30", "09:00", false, null),
        request);
    assertInvalid(
        new ActionProposal(ReminderIntent.CREATE, null, "2026-01-16", "09:00", false, null),
        request);
    assertInvalid(
        new ActionProposal(
            ReminderIntent.CLARIFY, null, null, null, true, " "),
        request);
    assertInvalid(
        new ActionProposal(
            ReminderIntent.CREATE, "Known task", "2026-01-16", "24:00", false, null),
        request);
  }

  @Test
  void rejectsDaylightSavingGapsAndFallBackOverlaps() {
    assertInvalid(
        new ActionProposal(
            ReminderIntent.CREATE, "Train", "2026-03-08", "02:30", false, null),
        request("2026-03-08T00:30:00-05:00", "America/New_York"));

    assertInvalid(
        new ActionProposal(
            ReminderIntent.CREATE, "Train", "2026-11-01", "01:30", false, null),
        request("2026-11-01T00:30:00-04:00", "America/New_York"));
  }

  @Test
  void rejectsReferenceOffsetThatDoesNotMatchTheIanaTimezone() {
    assertInvalid(
        new ActionProposal(
            ReminderIntent.CREATE, "Known task", "2026-01-16", "09:00", false, null),
        request("2026-01-15T10:00:00Z", "America/New_York"));
  }

  @Test
  void allowsClarificationToOmitUnknownTaskDateAndTime() {
    assertDoesNotThrow(
        () ->
            validator.validate(
                new ActionProposal(
                    ReminderIntent.CLARIFY,
                    null,
                    null,
                    null,
                    true,
                    "What date and time should I use?"),
                request("2026-01-15T10:00:00-05:00", "America/New_York")));
  }

  private void assertInvalid(ActionProposal proposal, ReminderRequest request) {
    assertThrows(
        ProposalValidationException.class, () -> validator.validate(proposal, request));
  }

  private ReminderRequest request(String referenceDatetime, String timezone) {
    return new ReminderRequest("Remind me about a known task.", referenceDatetime, timezone);
  }

  private ReminderRequest request(String text, String referenceDatetime, String timezone) {
    return new ReminderRequest(text, referenceDatetime, timezone);
  }
}
