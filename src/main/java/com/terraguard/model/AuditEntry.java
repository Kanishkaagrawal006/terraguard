package com.terraguard.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_entries")
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String repo;
    private Integer prNumber;
    private String sha;

    @Column(length = 20)
    private String decision; // APPROVE / REJECT

    @Column(length = 2000)
    private String reason;

    private String slackUserId;

    @Column(length = 4000)
    private String risksSnapshot;

    private Instant decidedAt = Instant.now();

    // getters/setters
    public Long getId() { return id; }
    public String getRepo() { return repo; }
    public void setRepo(String repo) { this.repo = repo; }
    public Integer getPrNumber() { return prNumber; }
    public void setPrNumber(Integer prNumber) { this.prNumber = prNumber; }
    public String getSha() { return sha; }
    public void setSha(String sha) { this.sha = sha; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getSlackUserId() { return slackUserId; }
    public void setSlackUserId(String slackUserId) { this.slackUserId = slackUserId; }
    public String getRisksSnapshot() { return risksSnapshot; }
    public void setRisksSnapshot(String risksSnapshot) { this.risksSnapshot = risksSnapshot; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
}
