# TerraGuard

> **An AI-powered infrastructure review agent for Slack.** Catch risky Terraform changes before they merge, and empower platform teams to review, approve, or reject PRs without leaving Slack.

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Terraform](https://img.shields.io/badge/Terraform-IaC-purple.svg)](https://www.terraform.io/)
[![Slack Agent Challenge](https://img.shields.io/badge/Slack%20Agent%20Challenge-Participant-4A154B.svg)](https://slack.com)
[![GitHub MCP](https://img.shields.io/badge/GitHub-MCP%20Integration-black.svg)](https://modelcontextprotocol.io/)

---

## The Problem

Reviewing Terraform diffs manually is tedious and error-prone. A simple one-line syntax update can accidentally expose a security group to the public internet or drop a critical production database. 

Reviewers often face two bad choices:
1. **Rubber-stamp PRs** to save time and risk production outages.
2. **Spend 20+ minutes** parsing raw, unreadable `terraform plan` outputs.

**TerraGuard converts raw plan JSON into a 30-second decision delivered directly inside Slack.**

---

## How It Works

```text
┌────────────────────────────────────────────────────────┐
│             Developer opens a Terraform PR              │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│  GitHub Action runs `terraform plan` -> JSON payload   │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│               TerraGuard Backend Service               │
├───────────────────────────┬────────────────────────────┤
│  Deterministic Risk Engine│   Groq LLM (Llama 3.3 70B) │
│  (JSON pattern-matching)  │   (Plain-English Summary)  │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│     Interactive Slack Alert (Block Kit UI)             │
│   • Readable Summary  • Risk Flags  • Action Buttons   │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│            Reviewer clicks Approve / Reject            │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│     GitHub MCP Server Call (JSON-RPC 2.0 Client)       │
│      • Posts Official PR Review  • Updates Labels      │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│      Decision Logged to PostgreSQL for Audit           │
└────────────────────────────────────────────────────────┘

```

### Core Design Principles

> **1. Deterministic Safety vs. Generative Summaries**
> Risk detection relies **exclusively on a deterministic rule engine** (pure JSON pattern-matching, zero LLM dependency). Safety-critical risk flags are auditable and reproducible. An LLM hallucination in the summary can **never hide or fabricate a security vulnerability**. The LLM's only job is making the plan human-readable.

> **2. Non-Destructive Gatekeeper**
> **TerraGuard never executes `terraform apply`.** Approving a PR inside Slack only writes a GitHub PR review and updates labels. Infrastructure modifications remain gated behind your existing CI/CD merge and deployment pipeline.

---

## Deterministic Risk Rules Engine

TerraGuard automatically scans raw plans for high-severity infrastructure anti-patterns:

| Category | Risk Condition Monitored |
| --- | --- |
| **Networking** | Security group ingress rules opened to `0.0.0.0/0` |
| **Database** | Database deletion or `deletion_protection = false` |
| **Security & Storage** | Storage encryption disabled or S3 bucket made public |
| **IAM Access** | Overly permissive policies granting `Action:*` on `Resource:*` |
| **Blast Radius** | High-impact changes exceeding **>5 resource destructions** |

---

## Tech Stack

| Component | Technology |
| --- | --- |
| **Backend Core** | Java 17, Spring Boot 3.3, Spring Data JPA |
| **Database** | PostgreSQL (Audit Trail & Pending State Tracking) |
| **AI Inference** | Groq API (`llama-3.3-70b-versatile`) |
| **Slack Integration** | Slack Bolt SDK, Block Kit, Signed Request Verification (HMAC) |
| **GitHub Integration** | GitHub MCP Server via Custom JSON-RPC 2.0 Client |
| **CI Trigger** | GitHub Actions Workflow (`terraform plan` -> Webhook) |
| **Deployment** | AWS EC2, Caddy Reverse Proxy (Automatic HTTPS via Let's Encrypt / sslip.io) |

---

## Deep-Dive: Native Model Context Protocol (MCP)

Instead of relying on standard REST calls, TerraGuard integrates directly with **GitHub's official MCP server**.

The backend communicates natively using **JSON-RPC 2.0** over HTTP (`tools/call`), calling endpoints like `pull_request_review_write` and `issue_write`. This approach establishes a true MCP tool-calling architecture.

> **Implementation details:** Check out `GitHubMcpClient.java` inside `src/main/java/com/terraguard/service/`.

---

## Project Structure

```text
src/main/java/com/terraguard/
├── controller/
│   ├── PlanWebhookController.java        # Ingests JSON plans from GitHub Actions
│   └── SlackInteractivityController.java # Handles Slack button events & modal submissions
├── service/
│   ├── RiskEngineService.java            # Deterministic rule engine logic
│   ├── GroqSummaryService.java           # AI plain-English summary generator
│   ├── SlackService.java                 # Renders Slack Block Kit UI components
│   ├── SlackSignatureVerifier.java       # Verifies Slack HMAC signatures
│   └── GitHubMcpClient.java              # Native MCP JSON-RPC 2.0 client implementation
├── model/                                # JPA Entities (AuditEntry, PendingReview, Risk)
├── repo/                                 # Spring Data JPA Repositories
└── config/                               # Typed Application Properties

.github/workflows/terraguard-plan.yml     # Reusable workflow triggering TerraGuard

```

---

## Demo Repository

Explore a live setup in the **[terraguard-demo](https://www.google.com/search?q=https://github.com/Kanishkaagrawal006/terraguard-demo)** repository. It contains sample Terraform code configured with GitHub Actions to demonstrate real-time risk catching and Slack approvals.

---

## Setup & Installation Guide

### 1. Create a Slack App

* Create a new Slack App with Bot Scopes: `chat:write`, `chat:write.public`.
* Enable **Interactivity** and point the Request URL to:
  `https://your-domain.com/slack/interactivity`

### 2. Configure GitHub MCP Access

Obtain access credentials for GitHub's hosted MCP server (`https://api.githubcopilot.com/mcp`) using a GitHub Personal Access Token scoped for `pull_requests: write` and `issues: write`.

### 3. Configure Environment Variables

```bash
TERRAGUARD_WEBHOOK_TOKEN=<shared-secret-with-github-actions>
SLACK_BOT_TOKEN=xoxb-your-slack-bot-token
SLACK_SIGNING_SECRET=your-slack-signing-secret
GITHUB_TOKEN=ghp_your_github_token
GITHUB_MCP_SERVER_URL=[https://api.githubcopilot.com/mcp](https://api.githubcopilot.com/mcp)
GROQ_API_KEY=gsk_your_groq_api_key
DB_USERNAME=postgres
DB_PASSWORD=your_database_password

```

### 4. Configure Repository Mapping

Define Slack channel mappings in `src/main/resources/application.yml`:

```yaml
terraguard:
  slack:
    repo-channel-map:
      "[your-org/your-terraform-repo]": C0123456789

```

### 5. Launch the Application

```bash
# Build and run the Spring Boot application
mvn clean spring-boot:run

```

*Note: Deployment behind an HTTPS endpoint is required by Slack for webhook interactions. You can use Caddy or Nginx as a reverse proxy.*

### 6. Attach GitHub Action to Target Repository

Add `.github/workflows/terraguard-plan.yml` to your Terraform repositories and set up repository secrets:

* `TERRAGUARD_URL`: Your deployed TerraGuard endpoint URL.
* `TERRAGUARD_WEBHOOK_TOKEN`: Matching shared secret token.

---
