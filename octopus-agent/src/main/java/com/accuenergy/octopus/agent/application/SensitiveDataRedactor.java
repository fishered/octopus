package com.accuenergy.octopus.agent.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Removes common credential fields before untrusted integration facts reach a model provider. */
@Component
public final class SensitiveDataRedactor {
    private static final String MASK = "[REDACTED]";
    private static final Pattern SENSITIVE_KEY = Pattern.compile(
            "(?i)(password|passwd|secret|token|api[-_]?key|authorization|credential|private[-_]?key|client[-_]?secret)");
    private static final Pattern INLINE_SECRET = Pattern.compile(
            "(?i)(password|passwd|secret|token|api[-_]?key|authorization|credential|private[-_]?key|client[-_]?secret)"
                    + "(\\s*[=:]\\s*)(\"[^\"]*\"|'[^']*'|[^\\s,;]+)");

    private final ObjectMapper json;

    public SensitiveDataRedactor(ObjectMapper json) {
        this.json = json;
    }

    public String redact(String input) {
        if (input == null || input.isBlank()) return input;
        try {
            JsonNode root = json.readTree(input);
            redactNode(root);
            return json.writeValueAsString(root);
        } catch (JsonProcessingException notJson) {
            return redactInline(input);
        }
    }

    private void redactNode(JsonNode node) {
        if (node instanceof ObjectNode object) {
            List<String> names = new ArrayList<>();
            object.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                JsonNode value = object.get(name);
                if (SENSITIVE_KEY.matcher(name).matches()) {
                    object.put(name, MASK);
                } else if (value.isTextual()) {
                    object.put(name, redactInline(value.textValue()));
                } else {
                    redactNode(value);
                }
            }
        } else if (node instanceof ArrayNode array) {
            for (int index = 0; index < array.size(); index++) {
                JsonNode item = array.get(index);
                if (item.isTextual()) array.set(index, json.getNodeFactory().textNode(redactInline(item.textValue())));
                else redactNode(item);
            }
        }
    }

    private static String redactInline(String value) {
        Matcher matcher = INLINE_SECRET.matcher(value);
        return matcher.replaceAll(match -> match.group(1) + match.group(2) + MASK);
    }
}
