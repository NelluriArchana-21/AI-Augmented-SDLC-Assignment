package com.assignment.reminderagent.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.assignment.reminderagent.parsing.MalformedModelOutputException;
import com.assignment.reminderagent.parsing.ModelOutputParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ModelOutputParserTest {

  private final ModelOutputParser parser = new ModelOutputParser(new ObjectMapper());

  @Test
  void parsesOneExactProposalObject() {
    var proposal =
        parser.parse(
            """
            {"intent":"CLARIFY","task":null,"date":null,"time":null,"needs_clarification":true,"clarification_question":"What time?"}
            """);

    assertEquals("CLARIFY", proposal.get("intent").textValue());
    assertEquals(6, proposal.size());
  }

  @Test
  void rejectsSurroundingProseExtraKeysTruncatedAndNonJsonCompletions() {
    for (String invalid :
        new String[] {
          "Here is the result: {\"intent\":\"CLARIFY\"}",
          "{\"intent\":\"CLARIFY\",\"task\":null,\"date\":null,\"time\":null,\"needs_clarification\":true,\"clarification_question\":\"When?\",\"extra\":1}",
          "{\"intent\":\"CLARIFY\"",
          "not JSON",
          "[]"
        }) {
      assertThrows(MalformedModelOutputException.class, () -> parser.parse(invalid), invalid);
    }
  }

  @Test
  void rejectsDuplicateKeysRatherThanSilentlyChoosingAValue() {
    String duplicate =
        """
        {"intent":"CREATE","intent":"CLARIFY","task":null,"date":null,"time":null,"needs_clarification":true,"clarification_question":"When?"}
        """;

    assertThrows(MalformedModelOutputException.class, () -> parser.parse(duplicate));
  }
}
