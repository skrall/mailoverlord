package org.mailoverlord.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Checks the generated OpenAPI document against what the endpoints actually return.
 *
 * <p>The response bodies cannot be asserted through MockMvc. An error status is applied via
 * {@code sendError}, which MockMvc never dispatches to the error handler, so every failure looks
 * like an empty body under test while a real server returns RFC 9457. That leaves the generated
 * document as the only place the contract is written down, which makes it worth pinning.
 *
 * <p>Two failure modes are guarded here. Declaring any {@code @ApiResponse} stops springdoc
 * inferring the success response, so an operation can end up documenting only its errors. And an
 * endpoint added without thinking about its failures ends up documenting only its success.
 */
class OpenApiResponseContractTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    private static final String PROBLEM_DETAIL_REF = "#/components/schemas/ProblemDetail";

    private static final Path SPEC = Path.of("ui", "openapi.json");

    private static final JsonNode DOCUMENT = readSpec();

    private record Operation(String method, String path, JsonNode responses) {
        List<String> statuses() {
            return names(responses);
        }

        @Override
        public String toString() {
            return method.toUpperCase() + " " + path;
        }
    }

    private static JsonNode readSpec() {
        try {
            return new ObjectMapper().readTree(Files.readString(SPEC));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + SPEC.toAbsolutePath()
                    + ". Run `npm run generate:spec` then `npm run generate:types` in ui/.", e);
        }
    }

    @Test
    void everyOperationDocumentsItsSuccessResponse() {
        for (Operation operation : operations()) {
            assertThat(operation.statuses())
                    .as("%s documents its success response", operation)
                    .contains("200");
        }
    }

    @Test
    void everyOperationDocumentsAtLeastOneFailureResponse() {
        for (Operation operation : operations()) {
            assertThat(operation.statuses())
                    .as("%s documents at least one 4xx response", operation)
                    .anyMatch(status -> status.startsWith("4"));
        }
    }

    @ParameterizedTest(name = "{0} {1} documents {2}")
    @CsvSource({
            "POST, /messages/delete, 400",
            "POST, /messages/release, 400",
            "GET, /messages/list, 400",
            "GET, /messages/{id}, 400",
            "GET, /messages/{id}, 404",
    })
    void operationDocumentsExpectedResponse(String method, String path, String status) {
        Operation operation = operation(method, path);
        assertThat(operation.statuses()).as("%s", operation).contains(status);
    }

    @Test
    void everyFailureResponseIsProblemDetail() {
        for (Operation operation : operations()) {
            for (String status : operation.statuses()) {
                if (!status.startsWith("4")) {
                    continue;
                }
                JsonNode content = operation.responses().get(status).get("content");
                assertThat(names(content))
                        .as("%s %s media type", operation, status)
                        .containsExactly(PROBLEM_JSON);
                assertThat(content.get(PROBLEM_JSON).get("schema").get("$ref").asText())
                        .as("%s %s schema", operation, status)
                        .isEqualTo(PROBLEM_DETAIL_REF);
            }
        }
    }

    @Test
    void everyReferencedSchemaIsDefined() {
        List<String> referenced = new ArrayList<>();
        collectRefs(DOCUMENT, referenced);
        List<String> defined = names(DOCUMENT.get("components").get("schemas"));
        for (String ref : referenced) {
            if (ref.startsWith("#/components/schemas/")) {
                assertThat(defined).as("schema referenced as %s", ref)
                        .contains(ref.substring("#/components/schemas/".length()));
            }
        }
    }

    private static Operation operation(String method, String path) {
        JsonNode node = DOCUMENT.get("paths").path(path).path(method.toLowerCase());
        assertThat(node.isMissingNode())
                .as("%s %s is present in the spec", method.toUpperCase(), path)
                .isFalse();
        return new Operation(method, path, node.get("responses"));
    }

    private static List<Operation> operations() {
        List<Operation> found = new ArrayList<>();
        DOCUMENT.get("paths").fields().forEachRemaining(pathEntry -> pathEntry.getValue().fields()
                .forEachRemaining(operationEntry -> {
                    if (!operationEntry.getKey().startsWith("x-")) {
                        found.add(new Operation(operationEntry.getKey(), pathEntry.getKey(),
                                operationEntry.getValue().get("responses")));
                    }
                }));
        return found;
    }

    private static List<String> names(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static void collectRefs(JsonNode node, List<String> into) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                if ("$ref".equals(entry.getKey())) {
                    into.add(entry.getValue().asText());
                } else {
                    collectRefs(entry.getValue(), into);
                }
            });
        } else if (node.isArray()) {
            node.forEach(child -> collectRefs(child, into));
        }
    }
}