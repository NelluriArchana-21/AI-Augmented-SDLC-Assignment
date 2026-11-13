package com.assignment.reminderagent.api;

import com.assignment.reminderagent.model.ActionProposal;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reminder-proposals")
public final class ReminderProposalController {

  private final ReminderProposalService service;

  public ReminderProposalController(ReminderProposalService service) {
    this.service = service;
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public ActionProposal propose(@RequestBody(required = false) JsonNode request) {
    return service.propose(request);
  }
}
