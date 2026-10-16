package com.lowenhert.patchmanagement.core.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Validates JSON documents against JSON Schema (draft-07). Compiled schemas are cached per schema node. */
@Component
public class JsonSchemaValidator {

    static final int MAX_ERRORS = 10;

    private final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    private final SchemaValidatorsConfig config = SchemaValidatorsConfig.builder().build();
    private final Map<JsonNode, JsonSchema> compiled = new ConcurrentHashMap<>();

    /** Returns human-readable violations (empty when valid), capped to keep repair prompts short. */
    public List<String> validate(JsonNode schema, JsonNode document) {
        JsonSchema compiledSchema = compiled.computeIfAbsent(schema, s -> factory.getSchema(s, config));
        return compiledSchema.validate(document).stream()
                .map(ValidationMessage::getMessage)
                .sorted(Comparator.naturalOrder())
                .limit(MAX_ERRORS)
                .toList();
    }
}
