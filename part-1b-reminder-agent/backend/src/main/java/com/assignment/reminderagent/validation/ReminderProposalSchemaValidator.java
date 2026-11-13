package com.assignment.reminderagent.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.ExecutionContext;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
public final class ReminderProposalSchemaValidator {

  private static final Consumer<ExecutionContext> ASSERT_FORMATS =
      context -> context.getExecutionConfig().setFormatAssertionsEnabled(Boolean.TRUE);

  private final JsonSchema schema;

  public ReminderProposalSchemaValidator(ObjectMapper objectMapper) {
    this.schema = loadSchema(objectMapper);
  }

  public void validate(JsonNode proposal) {
    if (proposal == null) {
      throw new ProposalValidationException(ProposalValidationException.Layer.SCHEMA);
    }

    Set<ValidationMessage> errors = schema.validate(proposal, ASSERT_FORMATS);
    if (!errors.isEmpty()) {
      throw new ProposalValidationException(ProposalValidationException.Layer.SCHEMA);
    }

    JsonNode dateNode = proposal.get("date");
    if (dateNode != null && !dateNode.isNull()) {
      try {
        LocalDate.parse(dateNode.textValue());
      } catch (DateTimeParseException exception) {
        throw new ProposalValidationException(ProposalValidationException.Layer.SCHEMA);
      }
    }
  }

  private JsonSchema loadSchema(ObjectMapper objectMapper) {
    InputStream schemaStream = getClass().getResourceAsStream("/schema/reminder-proposal.schema.json");
    if (schemaStream == null) {
      throw new IllegalStateException("Normative reminder proposal schema is unavailable");
    }
    try (schemaStream) {
      JsonNode schemaNode = objectMapper.readTree(schemaStream);
      return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaNode);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to load normative reminder proposal schema");
    }
  }
}
