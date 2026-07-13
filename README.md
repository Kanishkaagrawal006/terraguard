# TerraGuard

**An AI infrastructure review agent for Slack.** TerraGuard catches risky Terraform changes before they merge, and lets platform teams approve or reject them without leaving Slack.

Built for the Slack Agent Builder Challenge — New Slack Agent track.

---

## The problem

Terraform diffs are hard to review at a glance. A one-line change can mean "open a security group to the entire internet" or "delete the production database." Reviewers either rubber-stamp PRs or spend 20 minutes parsing raw `terraform plan` output. TerraGuard turns that into a 30-second Slack decision.

## How it works

```
Developer opens a Terraform PR
        |
        v
GitHub Action runs `terraform plan` -> `terraform show -json`
        |
        v
TerraGuard backend receives the plan JSON
        |
        +--> Risk Rule Engine (deterministic checks - no LLM)
        |
        +--> Groq LLM (plain-English summary)
        |
        v
Slack message posted: summary + risk flags + Approve/Reject/View Full Plan buttons
        |
        v
Reviewer clicks a button in Slack
        |
        v
GitHub MCP server call -> real PR review + label posted on GitHub
        |
        v
Decision logged to Postgres for audit history
```

**Key design decision:** risk detection is fully deterministic (pure JSON pattern-matching, no LLM involved), and kept strictly separate from the AI-generated summary. Safety-relevant flags are auditable and reproducible - a hallucination in the summary can never hide or fabricate a real risk. The LLM's only job is making the plan readable, not deciding what's dangerous.

**Nothing in this pipeline runs `terraform apply`.** Approving a PR in Slack only posts a GitHub review and label - a human still merges, and your existing CD pipeline applies as normal. TerraGuard is a review/gate layer, not an actor with write access to real infrastructure.

## Risk rules (MVP)

- Security group ingress opened to `0.0.0.0/0`
- Database deletion
- Deletion protection disabled
- Storage encryption disabled
- S3 bucket made public
- IAM policy granting `Action:*` on `Resource:*`
- Large blast radius (>5 resources destroyed in one plan)

## Tech stack

- **Backend:** Java 17, Spring Boot 3.3, Spring Data JPA
- **Database:** PostgreSQL (audit log + pending review tracking)
- **AI summary:** Groq API (Llama 3.3 70B)
- **Slack integration:** Slack Bolt SDK, Block Kit, signed request verification (HMAC)
- **GitHub integration:** GitHub's MCP server via a JSON-RPC client - real MCP tool calls (`pull_request_review_write`, `issue_write`), not raw REST
- **CI trigger:** GitHub Actions (`terraform plan` -> JSON -> webhook)
- **Deployment:** AWS EC2, Caddy (automatic HTTPS via Let's Encrypt / sslip.io)

## Why MCP

TerraGuard's GitHub integration goes through GitHub's official MCP server rather than hand-rolled REST calls. The backend speaks JSON-RPC 2.0 directly to the MCP endpoint (`tools/call`), the same protocol an LLM agent would use - meaning the approve/reject loop is a genuine MCP integration, not a decorative one. See `GitHubMcpClient.java` under `src/main/java/com/terraguard/service/`.

## Project structure

```
src/main/java/com/terraguard/
├── controller/
│   ├── PlanWebhookController.java        # receives plan JSON from GitHub Action
│   └── SlackInteractivityController.java # handles button clicks + modal submission
├── service/
│   ├── RiskEngineService.java            # deterministic risk rules
│   ├── GroqSummaryService.java           # AI plain-English summary
│   ├── SlackService.java                 # Block Kit messages, modals
│   ├── SlackSignatureVerifier.java       # HMAC request verification
│   └── GitHubMcpClient.java              # MCP JSON-RPC client
├── model/                                # JPA entities (AuditEntry, PendingReview, Risk)
├── repo/                                 # Spring Data repositories
└── config/                               # typed application properties

.github/workflows/terraguard-plan.yml     # the GitHub Action that triggers everything
```

## Demo repo

See the `terraguard-demo` repo (github.com/Kanishkaagrawal006/terraguard-demo) for a live example Terraform repo wired up with the GitHub Action, showing TerraGuard catching real risky changes on real pull requests.

## Setup

### 1. Slack app
Create a Slack app with bot scopes `chat:write`, `chat:write.public`. Enable Interactivity, pointing at `https://your-domain/slack/interactivity`.

### 2. GitHub MCP access
Use GitHub's hosted MCP server (`https://api.githubcopilot.com/mcp`) with a token scoped for `pull_requests: write` and `issues: write` on your target repo.

### 3. Environment variables

```
TERRAGUARD_WEBHOOK_TOKEN=<shared secret with your GitHub Action>
SLACK_BOT_TOKEN=xoxb-...
SLACK_SIGNING_SECRET=...
GITHUB_TOKEN=...
GITHUB_MCP_SERVER_URL=https://api.githubcopilot.com/mcp
GROQ_API_KEY=...
DB_USERNAME=postgres
DB_PASSWORD=...
```

### 4. Map your repo to a Slack channel

In `application.yml`:
```yaml
terraguard:
  slack:
    repo-channel-map:
      "[your-org/your-terraform-repo]": C0123456789
```

### 5. Run

```
mvn spring-boot:run
```

Deploy behind HTTPS (Slack requires it for the Interactivity URL) - see the repo for a Caddy-based reverse proxy setup.

### 6. Wire up the GitHub Action

Copy `.github/workflows/terraguard-plan.yml` into your Terraform repo, and add `TERRAGUARD_URL` + `TERRAGUARD_WEBHOOK_TOKEN` as repo secrets.

## What's not built (intentional scope for a hackathon timeline)

- Cost estimation
- Policy-as-code engine (OPA/Sentinel) - current rules are hardcoded checks
- Multi-cloud rule coverage (currently AWS-focused)
- Automated `terraform apply` - deliberately excluded; a human always merges

## License

Built for the Slack Agent Builder Challenge, 2026.
