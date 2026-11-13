package com.assignment.reminderagent.validation;

import com.assignment.reminderagent.model.ActionProposal;
import com.assignment.reminderagent.model.ReminderIntent;
import com.assignment.reminderagent.model.ReminderRequest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class ReminderRequestValidator {

  private static final DateTimeFormatter RFC_3339_FORMATTER =
      new DateTimeFormatterBuilder()
          .parseCaseInsensitive()
          .appendPattern("uuuu-MM-dd'T'HH:mm:ss")
          .optionalStart()
          .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
          .optionalEnd()
          .appendOffset("+HH:MM", "Z")
          .toFormatter(Locale.ROOT)
          .withResolverStyle(ResolverStyle.STRICT);

  public Result validate(JsonNode input) {
    if (input == null || !input.isObject()) {
      return clarify("Please provide a valid reminder request and time context.");
    }

    JsonNode requestNode = input.get("natural_language_request");
    if (requestNode == null || !requestNode.isTextual() || requestNode.textValue().isBlank()) {
      return clarify("What would you like to be reminded about?");
    }

    String requestText = requestNode.textValue();
    if (requestText.length() > ReminderRequest.MAX_NATURAL_LANGUAGE_REQUEST_LENGTH) {
      return clarify("Please shorten the reminder request to 4,000 characters or fewer.");
    }

    JsonNode datetimeNode = input.get("reference_datetime");
    if (datetimeNode == null || !datetimeNode.isTextual() || datetimeNode.textValue().isBlank()) {
      return clarify("Please provide a reference date and time in RFC 3339 format with an offset.");
    }

    OffsetDateTime referenceDateTime;
    try {
      referenceDateTime =
          OffsetDateTime.parse(datetimeNode.textValue(), RFC_3339_FORMATTER);
    } catch (DateTimeException exception) {
      return clarify("Please provide a valid RFC 3339 reference date and time with an offset.");
    }

    JsonNode timezoneNode = input.get("timezone");
    if (timezoneNode == null || !timezoneNode.isTextual() || timezoneNode.textValue().isBlank()) {
      return clarify("Please provide a valid IANA timezone identifier.");
    }

    String timezone = timezoneNode.textValue();
    if (!ZoneId.getAvailableZoneIds().contains(timezone)) {
      return clarify("Please provide a valid IANA timezone identifier.");
    }

    ZoneId zoneId = ZoneId.of(timezone);
    if (!zoneId.getRules().getOffset(referenceDateTime.toInstant()).equals(referenceDateTime.getOffset())) {
      return clarify("Please provide a reference timestamp whose offset matches the IANA timezone.");
    }

    return new Valid(new ReminderRequest(requestText, datetimeNode.textValue(), timezone));
  }

  private Clarification clarify(String question) {
    return new Clarification(
        new ActionProposal(ReminderIntent.CLARIFY, null, null, null, true, question));
  }

  public sealed interface Result permits Valid, Clarification {}

  public record Valid(ReminderRequest request) implements Result {
    public Valid {
      Objects.requireNonNull(request, "request");
    }
  }

  public record Clarification(ActionProposal proposal) implements Result {
    public Clarification {
      Objects.requireNonNull(proposal, "proposal");
    }
  }
}
