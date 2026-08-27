package com.scalegrams.training;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

/** Accepts both the list form used by API clients and the legacy comma-separated form. */
public class StringOrStringListDeserializer extends StdDeserializer<List<String>> {
    public StringOrStringListDeserializer() {
        super(List.class);
    }

    @Override
    public List<String> deserialize(JsonParser parser, com.fasterxml.jackson.databind.DeserializationContext context)
            throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            node.elements().forEachRemaining(item -> values.add(item.isNull() ? null : item.asText()));
        } else if (node.isTextual()) {
            for (String value : node.asText().split(",")) values.add(value);
        } else if (!node.isNull()) {
            context.reportInputMismatch(List.class, "Expected a string or an array of strings");
        }
        return values;
    }
}
