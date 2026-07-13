package com.terraguard.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "pending_reviews")
public class PendingReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String repo;
    private Integer prNumber;
    private String sha;
    private String slackChannel;
    private String slackTs;

    @Column(length = 20)
    private String status = "PENDING"; // PENDING / APPROVED / REJECTED

    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getRepo() { return repo; }
    public void setRepo(String repo) { this.repo = repo; }
    public Integer getPrNumber() { return prNumber; }
    public void setPrNumber(Integer prNumber) { this.prNumber = prNumber; }
    public String getSha() { return sha; }
    public void setSha(String sha) { this.sha = sha; }
    public String getSlackChannel() { return slackChannel; }
    public void setSlackChannel(String slackChannel) { this.slackChannel = slackChannel; }
    public String getSlackTs() { return slackTs; }
    public void setSlackTs(String slackTs) { this.slackTs = slackTs; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
