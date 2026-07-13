package com.terraguard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "terraguard")
public class AppProperties {

    private String webhookToken;
    private Slack slack = new Slack();
    private Github github = new Github();
    private Groq groq = new Groq();

    // ---------------- Slack ----------------

    public static class Slack {
        private String botToken;
        private String signingSecret;
        private Map<String, String> repoChannelMap;

        public String getBotToken() {
            return botToken;
        }

        public void setBotToken(String botToken) {
            this.botToken = botToken;
        }

        public String getSigningSecret() {
            return signingSecret;
        }

        public void setSigningSecret(String signingSecret) {
            this.signingSecret = signingSecret;
        }

        public Map<String, String> getRepoChannelMap() {
            return repoChannelMap;
        }

        public void setRepoChannelMap(Map<String, String> repoChannelMap) {
            this.repoChannelMap = repoChannelMap;
        }
    }

    // ---------------- GitHub ----------------

    public static class Github {
        private String token;
        private String mcpServerUrl;

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getMcpServerUrl() {
            return mcpServerUrl;
        }

        public void setMcpServerUrl(String mcpServerUrl) {
            this.mcpServerUrl = mcpServerUrl;
        }
    }

    // ---------------- Groq ----------------

    public static class Groq {
        private String apiKey;
        private String model;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    // ---------------- Main Properties ----------------

    public String getWebhookToken() {
        return webhookToken;
    }

    public void setWebhookToken(String webhookToken) {
        this.webhookToken = webhookToken;
    }

    public Slack getSlack() {
        return slack;
    }

    public void setSlack(Slack slack) {
        this.slack = slack;
    }

    public Github getGithub() {
        return github;
    }

    public void setGithub(Github github) {
        this.github = github;
    }

    public Groq getGroq() {
        return groq;
    }

    public void setGroq(Groq groq) {
        this.groq = groq;
    }
}