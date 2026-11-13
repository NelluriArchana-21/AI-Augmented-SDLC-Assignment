package com.assignment.reminderagent.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ActionProposal(
    @JsonProperty("intent") ReminderIntent intent,
    @JsonProperty("task") String task,
    @JsonProperty("date") String date,
    @JsonProperty("time") String time,
    @JsonProperty("needs_clarification") boolean needsClarification,
    @JsonProperty("clarification_question") String clarificationQuestion) {}
