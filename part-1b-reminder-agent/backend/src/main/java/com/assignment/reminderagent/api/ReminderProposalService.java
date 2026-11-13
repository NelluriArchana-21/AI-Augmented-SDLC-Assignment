package com.assignment.reminderagent.api;

import com.assignment.reminderagent.model.ActionProposal;
import com.assignment.reminderagent.provider.ProviderOperationalException;
import com.assignment.reminderagent.provider.ProviderResult;
import com.assignment.reminderagent.provider.ReminderExtractionProvider;
import com.assignment.reminderagent.prompt.ReminderPromptBuilder;
import com.assignment.reminderagent.validation.ProposalValidationException;
import com.assignment.reminderagent.validation.ReminderProposalInvariantValidator;
import com.assignment.reminderagent.validation.ReminderProposalSchemaValidator;
import com.assignment.reminderagent.validation.ReminderRequestValidator;
import com.assignment.reminderagent.validation.ReminderRequestValidator.Clarification;
import com.assignment.reminderagent.validation.ReminderRequestValidator.Valid;
import com.assignment.reminderagent.parsing.MalformedModelOutputException;
import com.assignment.reminderagent.parsing.ModelOutputParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public final class ReminderProposalService {

  private final ReminderRequestValidator requestValidator;
  private final ReminderPromptBuilder promptBuilder;
  private final ReminderExtractionProvider extractionProvider;
  private final ModelOutputParser outputParser;
  private final ReminderProposalSchemaValidator schemaValidator;
  private final ReminderProposalInvariantValidator invariantValidator;
  private final ObjectMapper objectMapper;

  public ReminderProposalService(
      ReminderRequestValidator requestValidator,
      ReminderPromptBuilder promptBuilder,
      ReminderExtractionProvider extractionProvider,
      ModelOutputParser outputParser,
      ReminderProposalSchemaValidator schemaValidator,
      ReminderProposalInvariantValidator invariantValidator,
      ObjectMapper objectMapper) {
    this.requestValidator = Objects.requireNonNull(requestValidator, "requestValidator");
    this.promptBuilder = Objects.requireNonNull(promptBuilder, "promptBuilder");
    this.extractionProvider = Objects.requireNonNull(extractionProvider, "extractionProvider");
    this.outputParser = Objects.requireNonNull(outputParser, "outputParser");
    this.schemaValidator = Objects.requireNonNull(schemaValidator, "schemaValidator");
    this.invariantValidator = Objects.requireNonNull(invariantValidator, "invariantValidator");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
  }

  public ActionProposal propose(JsonNode input) {
    ReminderRequestValidator.Result requestResult = requestValidator.validate(input);
    if (requestResult instanceof Clarification clarification) {
      return clarification.proposal();
    }

    Valid validRequest = (Valid) requestResult;
    ReminderPromptBuilder.Prompt prompt = promptBuilder.build(validRequest.request());
    ProviderResult providerResult =
        extractionProvider.extract(prompt.systemInstruction(), prompt.userContent());
    if (providerResult instanceof ProviderResult.OperationalFailure failure) {
      throw new ProviderOperationalException(failure.category());
    }
    if (!(providerResult instanceof ProviderResult.Success success)) {
      throw new IllegalStateException("Provider returned an unsupported result type");
    }

    JsonNode proposalNode = outputParser.parse(success.rawContent());
    schemaValidator.validate(proposalNode);

    ActionProposal proposal;
    try {
      proposal = objectMapper.treeToValue(proposalNode, ActionProposal.class);
    } catch (JsonProcessingException exception) {
      throw new MalformedModelOutputException();
    }
    invariantValidator.validate(proposal, validRequest.request());
    return proposal;
  }
}
