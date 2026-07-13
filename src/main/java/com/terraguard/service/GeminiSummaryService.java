package com.terraguard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.terraguard.config.AppProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class GeminiSummaryService {

    private final AppProperties props;
    private final ObjectMapper mapper = new ObjectMapper();

    private final RestClient restClient =
            RestClient.builder()
                    .baseUrl("https://api.groq.com")
                    .build();

    public GeminiSummaryService(AppProperties props) {
        this.props = props;
    }

    public String summarize(JsonNode plan, String prTitle) {

        String changesDigest = digestChanges(plan);

        String prompt = """
                Summarize this Terraform plan for a Slack message.

                Requirements:
                - 2-3 short sentences.
                - Plain English.
                - No markdown.
                - Mention how many resources will be added, changed, and destroyed.
                - Mention any important infrastructure impact.

                PR Title:
                %s

                Resource Changes:
                %s
                """.formatted(prTitle, changesDigest);

        Map<String, Object> body = Map.of(
                "model", props.getGroq().getModel(),
                "messages", List.of(
                        Map.of(
                                "role", "user",
                                "content", prompt
                        )
                ),
                "temperature", 0.2
        );

        try {

            String response = restClient.post()
                    .uri("/openai/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + props.getGroq().getApiKey())
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode json = mapper.readTree(response);

            JsonNode choices = json.path("choices");

            if (choices.isArray() && !choices.isEmpty()) {
                return choices.get(0)
                        .path("message")
                        .path("content")
                        .asText();
            }

            return "Summary unavailable. See the Terraform plan for details.";

        } catch (Exception e) {
            return "AI summary unavailable (" + e.getMessage()
                    + "). See the Terraform plan for details.";
        }
    }

    /**
     * Creates a compact representation of Terraform resource changes
     * for the AI to summarize.
     */
    private String digestChanges(JsonNode plan) {

        ArrayNode digest = mapper.createArrayNode();

        for (JsonNode rc : plan.path("resource_changes")) {

            ObjectNode entry = mapper.createObjectNode();

            entry.put("address", rc.path("address").asText());
            entry.put("type", rc.path("type").asText());
            entry.set("actions", rc.path("change").path("actions"));

            digest.add(entry);
        }

        return digest.toPrettyString();
    }
}