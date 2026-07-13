package com.terraguard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.terraguard.model.Risk;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic, rule-based risk detection over a `terraform show -json` plan.
 * Kept separate from the LLM summary on purpose: safety-relevant flags must be
 * auditable and reproducible, not subject to model variance.
 */
@Service
public class RiskEngineService {

    private static final int LARGE_BLAST_RADIUS_THRESHOLD = 5;

    public List<Risk> findRisks(JsonNode plan) {
        List<Risk> risks = new ArrayList<>();
        JsonNode changes = plan.path("resource_changes");
        int destroyCount = 0;

        for (JsonNode rc : changes) {
            String type = rc.path("type").asText();
            String address = rc.path("address").asText();
            JsonNode actions = rc.path("change").path("actions");
            JsonNode after = rc.path("change").path("after");
            boolean isDelete = containsAction(actions, "delete");
            if (isDelete) destroyCount++;

            switch (type) {
                case "aws_security_group_rule", "aws_security_group" -> checkOpenIngress(after, address, risks);
                case "aws_db_instance", "aws_rds_cluster" -> checkDatabase(after, address, isDelete, risks);
                case "aws_s3_bucket" -> checkPublicBucket(after, address, risks);
                case "aws_iam_policy", "aws_iam_role_policy" -> checkIamWildcard(after, address, risks);
                default -> { /* not a rule we check */ }
            }
        }

        if (destroyCount > LARGE_BLAST_RADIUS_THRESHOLD) {
            risks.add(new Risk(Risk.Severity.MEDIUM, "plan-wide",
                    destroyCount + " resources will be destroyed in this plan"));
        }

        return risks;
    }

    private boolean containsAction(JsonNode actions, String value) {
        if (!actions.isArray()) return false;
        for (JsonNode a : actions) {
            if (a.asText().equals(value)) return true;
        }
        return false;
    }

    private void checkOpenIngress(JsonNode after, String address, List<Risk> risks) {
        if (after == null || after.isMissingNode()) return;
        String direction = after.path("type").asText(""); // ingress/egress on sg_rule
        JsonNode cidrBlocks = after.has("cidr_blocks") ? after.path("cidr_blocks") : after.path("cidr_ipv4");
        boolean isIngress = "ingress".equals(direction) || !after.has("type"); // sg resource itself has no 'type'
        if (!isIngress) return;

        if (cidrBlocks.isArray()) {
            for (JsonNode cidr : cidrBlocks) {
                if ("0.0.0.0/0".equals(cidr.asText())) {
                    risks.add(new Risk(Risk.Severity.HIGH, address,
                            "Security group opens ingress to 0.0.0.0/0"));
                    return;
                }
            }
        } else if ("0.0.0.0/0".equals(cidrBlocks.asText())) {
            risks.add(new Risk(Risk.Severity.HIGH, address,
                    "Security group opens ingress to 0.0.0.0/0"));
        }
    }

    private void checkDatabase(JsonNode after, String address, boolean isDelete, List<Risk> risks) {
        if (isDelete) {
            risks.add(new Risk(Risk.Severity.HIGH, address, "Database is being deleted"));
        }
        if (after != null && after.has("deletion_protection")
                && !after.path("deletion_protection").asBoolean(true)) {
            risks.add(new Risk(Risk.Severity.MEDIUM, address, "Deletion protection is disabled"));
        }
        if (after != null && after.has("storage_encrypted")
                && !after.path("storage_encrypted").asBoolean(true)) {
            risks.add(new Risk(Risk.Severity.HIGH, address, "Storage encryption is disabled"));
        }
    }

    private void checkPublicBucket(JsonNode after, String address, List<Risk> risks) {
        if (after == null) return;
        String acl = after.path("acl").asText("");
        if ("public-read".equals(acl) || "public-read-write".equals(acl)) {
            risks.add(new Risk(Risk.Severity.HIGH, address, "S3 bucket ACL is set to " + acl));
        }
    }

    private void checkIamWildcard(JsonNode after, String address, List<Risk> risks) {
        if (after == null || !after.has("policy")) return;
        String policyText = after.path("policy").asText("");
        // Cheap string check is sufficient for a demo; a production version should
        // parse the policy JSON properly and check each Statement entry.
        if (policyText.contains("\"Action\": \"*\"") || policyText.contains("\"Action\":\"*\"")) {
            if (policyText.contains("\"Resource\": \"*\"") || policyText.contains("\"Resource\":\"*\"")) {
                risks.add(new Risk(Risk.Severity.HIGH, address,
                        "IAM policy grants Action:* on Resource:*"));
            }
        }
    }
}
