package com.assignment.reminderagent.validation;

import com.assignment.reminderagent.model.ActionProposal;
import com.assignment.reminderagent.model.ReminderIntent;
import com.assignment.reminderagent.model.ReminderRequest;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public final class ReminderProposalInvariantValidator {

  private static final DateTimeFormatter LOCAL_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
  private static final Pattern SNOOZE_DELAY_PATTERN =
      Pattern.compile(
          "(?i)\\b(?:for|in|by)\\s+(\\d+)\\s+(seconds?|minutes?|mins?|hours?|hrs?|days?|weeks?)\\b");

  public void validate(ActionProposal proposal, ReminderRequest request) {
    if (proposal == null || request == null) {
      throw invalid();
    }

    ZoneId zone = validatedZone(request);
    validateContractInvariants(proposal, request);

    LocalDate date = parseDate(proposal.date());
    LocalTime time = parseTime(proposal.time());
    if (date != null && time != null) {
      List<ZoneOffset> validOffsets =
          zone.getRules().getValidOffsets(LocalDateTime.of(date, time));
      if (validOffsets.size() != 1) {
        throw invalid();
      }
      if (proposal.intent() == ReminderIntent.SNOOZE) {
        validateSnoozeTargetDateTime(proposal, request, zone);
      }
    }
  }

  private ZoneId validatedZone(ReminderRequest request) {
    if (request.naturalLanguageRequest() == null
        || request.naturalLanguageRequest().isBlank()
        || request.naturalLanguageRequest().length()
            > ReminderRequest.MAX_NATURAL_LANGUAGE_REQUEST_LENGTH
        || request.timezone() == null
        || request.referenceDatetime() == null
        || !ZoneId.getAvailableZoneIds().contains(request.timezone())) {
      throw invalid();
    }
    try {
      OffsetDateTime reference =
          OffsetDateTime.parse(request.referenceDatetime(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
      ZoneId zone = ZoneId.of(request.timezone());
      if (!zone.getRules().getOffset(reference.toInstant()).equals(reference.getOffset())) {
        throw invalid();
      }
      return zone;
    } catch (DateTimeException exception) {
      throw invalid();
    }
  }

  private void validateContractInvariants(ActionProposal proposal, ReminderRequest request) {
    ReminderIntent intent = proposal.intent();
    if (intent == null) {
      throw invalid();
    }

    boolean clarification = intent == ReminderIntent.CLARIFY;
    if (proposal.needsClarification() != clarification) {
      throw invalid();
    }
    if (clarification) {
      if (proposal.clarificationQuestion() == null
          || proposal.clarificationQuestion().isBlank()) {
        throw invalid();
      }
    } else {
      if (proposal.clarificationQuestion() != null
          || proposal.task() == null
          || proposal.task().isBlank()
          || proposal.date() == null
          || proposal.time() == null) {
        throw invalid();
      }
    }
    if (proposal.task() != null
        && (proposal.task().isBlank() || proposal.task().length() > 120)) {
      throw invalid();
    }
    if ((intent == ReminderIntent.RESCHEDULE || intent == ReminderIntent.SNOOZE)
        && !containsGroundedTarget(request.naturalLanguageRequest(), proposal.task())) {
      throw invalid();
    }
    if (intent == ReminderIntent.SNOOZE
        && parseSnoozeDelay(request.naturalLanguageRequest()) == null) {
      throw invalid();
    }
  }

  private boolean containsGroundedTarget(String request, String target) {
    if (target == null || target.isBlank()) {
      return false;
    }
    String normalizedRequest = normalizeTargetText(request);
    String normalizedTarget = normalizeTargetText(target);
    return !normalizedTarget.isEmpty() && normalizedRequest.contains(normalizedTarget);
  }

  private String normalizeTargetText(String value) {
    return value
        .toLowerCase(Locale.ROOT)
        .replaceAll("[\\\"'`“”‘’]", "")
        .replaceAll("\\s+", " ")
        .trim();
  }

  private void validateSnoozeTargetDateTime(
      ActionProposal proposal, ReminderRequest request, ZoneId zone) {
    Duration delay = parseSnoozeDelay(request.naturalLanguageRequest());
    if (delay == null) {
      throw invalid();
    }
    try {
      OffsetDateTime reference =
          OffsetDateTime.parse(
              request.referenceDatetime(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
      LocalDateTime expected =
          reference.toInstant().plus(delay).atZone(zone).toLocalDateTime();
      LocalDate proposedDate = parseDate(proposal.date());
      LocalTime proposedTime = parseTime(proposal.time());
      if (!expected.toLocalDate().equals(proposedDate)
          || !expected.toLocalTime().equals(proposedTime)) {
        throw invalid();
      }
    } catch (DateTimeException | ArithmeticException exception) {
      throw invalid();
    }
  }

  private Duration parseSnoozeDelay(String request) {
    Matcher matcher = SNOOZE_DELAY_PATTERN.matcher(request);
    if (!matcher.find()) {
      return null;
    }
    String amountText = matcher.group(1);
    String unit = matcher.group(2).toLowerCase(Locale.ROOT);
    if (matcher.find()) {
      return null;
    }
    try {
      long amount = Long.parseLong(amountText);
      if (amount <= 0) {
        return null;
      }
      if (unit.startsWith("second")) {
        return Duration.ofSeconds(amount);
      }
      if (unit.startsWith("minute") || unit.startsWith("min")) {
        return Duration.ofMinutes(amount);
      }
      if (unit.startsWith("hour") || unit.startsWith("hr")) {
        return Duration.ofHours(amount);
      }
      long days = unit.startsWith("week") ? Math.multiplyExact(amount, 7) : amount;
      return Duration.ofDays(days);
    } catch (DateTimeException | ArithmeticException exception) {
      return null;
    }
  }

  private LocalDate parseDate(String date) {
    if (date == null) {
      return null;
    }
    try {
      return LocalDate.parse(date);
    } catch (DateTimeException exception) {
      throw invalid();
    }
  }

  private LocalTime parseTime(String time) {
    if (time == null) {
      return null;
    }
    try {
      return LocalTime.parse(time, LOCAL_TIME_FORMATTER);
    } catch (DateTimeException exception) {
      throw invalid();
    }
  }

  private ProposalValidationException invalid() {
    return new ProposalValidationException(ProposalValidationException.Layer.INVARIANT);
  }
}
