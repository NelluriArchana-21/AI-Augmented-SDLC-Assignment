package com.assignment.reminderagent.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

@JsonTest
class JsonSchemaDependencyCompatibilityTest {

  @Autowired private ObjectMapper objectMapper;

  @Test
  void validatesDraft202012SchemaWithSpringManagedJackson() throws Exception {
    assertEquals("2.17.2", objectMapper.version().toString());

    var schemaNode =
        objectMapper.readTree(
            """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "required": ["value"],
              "properties": {"value": {"type": "string"}},
              "additionalProperties": false
            }
            """);
    var schema =
        JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaNode);

    assertTrue(schema.validate(objectMapper.readTree("{\"value\":\"ok\"}")).isEmpty());
    assertFalse(schema.validate(objectMapper.readTree("{\"value\":42}")).isEmpty());
  }
}
