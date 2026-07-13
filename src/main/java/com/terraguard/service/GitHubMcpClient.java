package com.terraguard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraguard.config.AppProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

@Service
public class GitHubMcpClient {

    private final AppProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient restClient;

    public GitHubMcpClient(AppProperties props) {
        this.props = props;
        this.restClient = RestClient.create(props.getGithub().getMcpServerUrl());
    }

    public JsonNode callTool(String toolName, Map<String, Object> arguments) {
        Map<String, Object> rpcRequest = Map.of(
                "jsonrpc", "2.0",
                "id", UUID.randomUUID().toString(),
                "method", "tools/call",
                "params", Map.of(
                        "name", toolName,
                        "arguments", arguments
                )
        );

        String response = restClient.post()
                .uri("")
                .header("Authorization", "Bearer " + props.getGithub().getToken())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .body(rpcRequest)
                .retrieve()
                .body(String.class);

        try {
            String jsonPayload = extractJsonPayload(response);
            JsonNode json = mapper.readTree(jsonPayload);
            if (json.has("error")) {
                throw new IllegalStateException("MCP tool call failed: " + json.get("error"));
            }
            return json.path("result");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse MCP response for tool " + toolName, e);
        }
    }

    private String extractJsonPayload(String response) {
        String trimmed = response.strip();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed;
        }
        String lastDataLine = null;
        for (String line : trimmed.split("\\r?\\n")) {
            if (line.startsWith("data:")) {
                lastDataLine = line.substring("data:".length()).strip();
            }
        }
        if (lastDataLine == null) {
            throw new IllegalStateException("No JSON payload found in MCP response: " + trimmed);
        }
        return lastDataLine;
    }

    public void approvePullRequest(String owner, String repo, int prNumber, String reviewerSlackId) {
        callTool("pull_request_review_write", Map.of(
                "method", "create",
                "owner", owner,
                "repo", repo,
                "pullNumber", prNumber,
                "event", "APPROVE",
                "body", "Approved via TerraGuard by <@" + reviewerSlackId + ">"
        ));
        addLabel(owner, repo, prNumber, "approved-by-slack");
    }

    public void requestChanges(String owner, String repo, int prNumber, String reason, String reviewerSlackId) {
        callTool("pull_request_review_write", Map.of(
                "method", "create",
                "owner", owner,
                "repo", repo,
                "pullNumber", prNumber,
                "event", "REQUEST_CHANGES",
                "body", "Rejected via TerraGuard by <@" + reviewerSlackId + ">\n\nReason: " + reason
        ));
        addLabel(owner, repo, prNumber, "rejected-by-slack");
    }

    private void addLabel(String owner, String repo, int prNumber, String label) {
        callTool("issue_write", Map.of(
                "method", "update",
                "owner", owner,
                "repo", repo,
                "issue_number", prNumber,
                "labels", new String[]{label}
        ));
    }
}