package com.terraguard.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraguard.model.AuditEntry;
import com.terraguard.model.PendingReview;
import com.terraguard.repo.AuditEntryRepository;
import com.terraguard.repo.PendingReviewRepository;
import com.terraguard.service.GitHubMcpClient;
import com.terraguard.service.SlackService;
import com.terraguard.service.SlackSignatureVerifier;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@RestController
public class SlackInteractivityController {

    private final SlackSignatureVerifier verifier;
    private final SlackService slackService;
    private final GitHubMcpClient mcpClient;
    private final PendingReviewRepository pendingReviewRepo;
    private final AuditEntryRepository auditRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    public SlackInteractivityController(SlackSignatureVerifier verifier, SlackService slackService,
                                        GitHubMcpClient mcpClient, PendingReviewRepository pendingReviewRepo,
                                        AuditEntryRepository auditRepo) {
        this.verifier = verifier;
        this.slackService = slackService;
        this.mcpClient = mcpClient;
        this.pendingReviewRepo = pendingReviewRepo;
        this.auditRepo = auditRepo;
    }

    @PostMapping(value = "/slack/interactivity", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> handle(HttpServletRequest request) throws Exception {

        String rawBody = readRawBody(request);

        String timestamp = request.getHeader("X-Slack-Request-Timestamp");
        String signature = request.getHeader("X-Slack-Signature");

        if (!verifier.isValid(timestamp, rawBody, signature)) {
            return ResponseEntity.status(401).body("invalid signature");
        }

        String payloadRaw = extractPayloadParam(rawBody);
        JsonNode payload = mapper.readTree(payloadRaw);
        String type = payload.path("type").asText();

        if ("block_actions".equals(type)) {
            return handleBlockAction(payload);
        } else if ("view_submission".equals(type)) {
            return handleModalSubmit(payload);
        }
        return ResponseEntity.ok("");
    }

    private String readRawBody(HttpServletRequest request) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            char[] buf = new char[1024];
            int read;
            while ((read = reader.read(buf)) != -1) {
                sb.append(buf, 0, read);
            }
        }
        return sb.toString();
    }

    private String extractPayloadParam(String rawBody) {
        String prefix = "payload=";
        String encoded = rawBody.startsWith(prefix) ? rawBody.substring(prefix.length()) : rawBody;
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    }

    private ResponseEntity<String> handleBlockAction(JsonNode payload) throws Exception {
        JsonNode action = payload.path("actions").get(0);
        String actionId = action.path("action_id").asText();
        String value = action.path("value").asText();
        String triggerId = payload.path("trigger_id").asText();
        String userId = payload.path("user").path("id").asText();

        if ("view_plan".equals(actionId)) {
            return ResponseEntity.ok("");
        }

        JsonNode valueNode = mapper.readTree(value);
        String repo = valueNode.path("repo").asText();
        int pr = valueNode.path("pr").asInt();
        String sha = valueNode.path("sha").asText();

        if ("approve_pr".equals(actionId)) {
            processDecision(repo, pr, sha, "APPROVE", null, userId, payload);
        } else if ("reject_pr".equals(actionId)) {
            slackService.openRejectReasonModal(triggerId, value + "|||" + userId + "|||"
                    + payload.path("channel").path("id").asText() + "|||" + payload.path("message").path("ts").asText());
        }

        return ResponseEntity.ok("");
    }

    private ResponseEntity<String> handleModalSubmit(JsonNode payload) throws Exception {
        String callbackId = payload.path("view").path("callback_id").asText();
        if (!"reject_reason_submit".equals(callbackId)) {
            return ResponseEntity.ok("");
        }

        String privateMetadata = payload.path("view").path("private_metadata").asText();
        String[] parts = privateMetadata.split("\\|\\|\\|");
        String value = parts[0];
        String userId = parts[1];
        String channel = parts[2];
        String ts = parts[3];

        String reason = payload.path("view").path("state").path("values")
                .path("reason_block").path("reason_input").path("value").asText("(no reason given)");

        JsonNode valueNode = mapper.readTree(value);
        String repo = valueNode.path("repo").asText();
        int pr = valueNode.path("pr").asInt();
        String sha = valueNode.path("sha").asText();

        processDecisionWithContext(repo, pr, sha, "REJECT", reason, userId, channel, ts);
        return ResponseEntity.ok("");
    }

    private void processDecision(String repo, int pr, String sha, String decision, String reason,
                                 String userId, JsonNode payload) throws Exception {
        String channel = payload.path("channel").path("id").asText();
        String ts = payload.path("message").path("ts").asText();
        processDecisionWithContext(repo, pr, sha, decision, reason, userId, channel, ts);
    }

    private void processDecisionWithContext(String repo, int pr, String sha, String decision, String reason,
                                            String userId, String channel, String ts) throws Exception {
        Optional<PendingReview> pendingOpt = pendingReviewRepo.findByRepoAndPrNumberAndSha(repo, pr, sha);
        if (pendingOpt.isPresent() && !"PENDING".equals(pendingOpt.get().getStatus())) {
            return;
        }

        String[] ownerRepo = repo.split("/");
        String owner = ownerRepo[0];
        String repoName = ownerRepo[1];

        if ("APPROVE".equals(decision)) {
            mcpClient.approvePullRequest(owner, repoName, pr, userId);
            slackService.updateWithOutcome(channel, ts, "✅ Approved by <@" + userId + ">");
        } else {
            mcpClient.requestChanges(owner, repoName, pr, reason, userId);
            slackService.updateWithOutcome(channel, ts,
                    "❌ Rejected by <@" + userId + ">\n*Reason:* " + reason);
        }

        pendingOpt.ifPresent(p -> {
            p.setStatus("APPROVE".equals(decision) ? "APPROVED" : "REJECTED");
            pendingReviewRepo.save(p);
        });

        AuditEntry entry = new AuditEntry();
        entry.setRepo(repo);
        entry.setPrNumber(pr);
        entry.setSha(sha);
        entry.setDecision(decision);
        entry.setReason(reason);
        entry.setSlackUserId(userId);
        auditRepo.save(entry);
    }
}