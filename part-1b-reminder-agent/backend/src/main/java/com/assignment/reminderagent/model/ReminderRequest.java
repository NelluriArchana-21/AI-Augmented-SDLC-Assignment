package com.assignment.reminderagent.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReminderRequest(
    @JsonProperty("natural_language_request")
        @NotBlank
        @Size(max = MAX_NATURAL_LANGUAGE_REQUEST_LENGTH)
        String naturalLanguageRequest,
    @JsonProperty("reference_datetime") @NotBlank String referenceDatetime,
    @NotBlank String timezone) {

  public static final int MAX_NATURAL_LANGUAGE_REQUEST_LENGTH = 4_000;
}
