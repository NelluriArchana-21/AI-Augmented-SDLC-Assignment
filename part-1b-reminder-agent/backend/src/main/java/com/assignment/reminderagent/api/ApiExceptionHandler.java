package com.assignment.reminderagent.api;

import com.assignment.reminderagent.model.ActionProposal;
import com.assignment.reminderagent.model.ReminderIntent;
import com.assignment.reminderagent.parsing.MalformedModelOutputException;
import com.assignment.reminderagent.provider.ProviderOperationalException;
import com.assignment.reminderagent.validation.ProposalValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(ProviderOperationalException.class)
  public ResponseEntity<OperationalErrorResponse> handleProviderOperationalFailure(
      ProviderOperationalException exception) {
    OperationalErrorResponse response =
        new OperationalErrorResponse(
            "OPERATIONAL_FAILURE", "The reminder proposal service is unavailable.");
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
  }

  @ExceptionHandler({MalformedModelOutputException.class, ProposalValidationException.class})
  public ResponseEntity<StructuralErrorResponse> handleInvalidProviderProposal(
      RuntimeException exception) {
    StructuralErrorResponse response =
        new StructuralErrorResponse(
            "STRUCTURAL_FAILURE", "The provider returned an invalid reminder proposal.");
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ActionProposal> handleMalformedRequestBody(
      HttpMessageNotReadableException exception) {
    ActionProposal response =
        new ActionProposal(
            ReminderIntent.CLARIFY,
            null,
            null,
            null,
            true,
            "Please provide a valid JSON reminder request.");
    return ResponseEntity.ok(response);
  }

  public record OperationalErrorResponse(String code, String message) {}

  public record StructuralErrorResponse(String code, String message) {}
}
