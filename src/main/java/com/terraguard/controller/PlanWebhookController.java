package com.terraguard.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraguard.config.AppProperties;
import com.terraguard.model.PendingReview;
import com.terraguard.model.Risk;
import com.terraguard.repo.PendingReviewRepository;
import com.terraguard.service.GroqSummaryService;
import com.terraguard.service.RiskEngineService;
import com.terraguard.service.SlackService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/webhook")
public class PlanWebhookController {

    private final AppProperties props;
    private final RiskEngineService riskEngine;
    private final GroqSummaryService claudeSummary;
    private final SlackService slackService;
    private final PendingReviewRepository pendingReviewRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    public PlanWebhookController(AppProperties props, RiskEngineService riskEngine,
                                 GroqSummaryService claudeSummary, SlackService slackService,
                                 PendingReviewRepository pendingReviewRepo) {
        this.props = props;
        this.riskEngine = riskEngine;
        this.claudeSummary = claudeSummary;
        this.slackService = slackService;
        this.pendingReviewRepo = pendingReviewRepo;
    }

    @PostMapping(value = "/plan", consumes = "multipart/form-data")
    public ResponseEntity<?> receivePlan(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("plan") MultipartFile planFile,
            @RequestParam("pr_number") Integer prNumber,
            @RequestParam("repo") String repo,
            @RequestParam("sha") String sha,
            @RequestParam(value = "pr_title", defaultValue = "") String prTitle,
            @RequestParam(value = "plan_url", defaultValue = "") String planUrl
    ) {
        String expected = "Bearer " + props.getWebhookToken();
        if (!expected.equals(authHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("bad token");
        }

        try {
            JsonNode plan = mapper.readTree(planFile.getInputStream());
            List<Risk> risks = riskEngine.findRisks(plan);
            String summary = claudeSummary.summarize(plan, prTitle);

            String channel = props.getSlack().getRepoChannelMap().get(repo);
            if (channel == null) {
                return ResponseEntity.badRequest().body("No Slack channel mapped for repo " + repo);
            }

            String buttonValue = mapper.writeValueAsString(
                    java.util.Map.of("repo", repo, "pr", prNumber, "sha", sha));

            var postResp = slackService.postReviewMessage(
                    channel, prTitle, prNumber, repo, summary, risks, planUrl, buttonValue);

            PendingReview pending = new PendingReview();
            pending.setRepo(repo);
            pending.setPrNumber(prNumber);
            pending.setSha(sha);
            pending.setSlackChannel(channel);
            pending.setSlackTs(postResp.getTs());
            pendingReviewRepo.save(pending);

            return ResponseEntity.ok().body("posted");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error: " + e.getMessage());
        }
    }
}
