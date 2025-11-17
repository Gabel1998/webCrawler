package org.ek.webcrawler.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Service til at kommunikere med Groq AI API
 * Bruges til at klassificere websider baseret på URL og titel
 */
@Service
@Slf4j
public class GroqService {

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}")
    private String apiUrl;

    @Value("${groq.api.model:llama-3.3-70b-versatile}")
    private String model;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GroqService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Send en chat completion request til Groq API
     *
     * @param systemPrompt System instruktioner til AI'en
     * @param userPrompt Brugerens prompt (side data)
     * @return AI'ens svar som string
     */
    public String getChatCompletion(String systemPrompt, String userPrompt) throws Exception {
        log.debug("Sender request til Groq API");

        // Byg request body
        GroqRequest request = GroqRequest.builder()
                .model(model)
                .messages(List.of(
                        Message.builder()
                                .role("system")
                                .content(systemPrompt)
                                .build(),
                        Message.builder()
                                .role("user")
                                .content(userPrompt)
                                .build()
                ))
                .temperature(0.0) // OPTIMIZED: 0.0 = mere deterministisk, kortere output
                .maxTokens(50)    // OPTIMIZED: Reduceret fra 100 til 50 (kun kategori-navn)
                .build();

        String requestBody = objectMapper.writeValueAsString(request);

        // Send HTTP request
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("Groq API fejl: {} - {}", response.statusCode(), response.body());
            throw new RuntimeException("Groq API request fejlede: " + response.statusCode());
        }

        // Parse response
        GroqResponse groqResponse = objectMapper.readValue(response.body(), GroqResponse.class);

        if (groqResponse.getChoices() == null || groqResponse.getChoices().isEmpty()) {
            throw new RuntimeException("Intet svar fra Groq API");
        }

        String content = groqResponse.getChoices().get(0).getMessage().getContent();
        log.debug("Modtog svar fra Groq: {}", content);

        return content.trim();
    }

    // ============================================
    // DTOs til Groq API kommunikation
    // ============================================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroqRequest {
        private String model;
        private List<Message> messages;
        private Double temperature;
        @JsonProperty("max_tokens")
        private Integer maxTokens;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role;
        private String content;
    }

    /**
     * Tilføjet alle felter som Groq API returnerer
     * Inkluderer @JsonIgnoreProperties for at ignorere ukendte felter
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GroqResponse {
        private String id;
        private String object;
        private Long created;
        private String model;
        private List<Choice> choices;
        private Usage usage;

        @JsonProperty("system_fingerprint")
        private String systemFingerprint;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {
        private Integer index;
        private Message message;
        @JsonProperty("finish_reason")
        private String finishReason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;
        @JsonProperty("completion_tokens")
        private Integer completionTokens;
        @JsonProperty("total_tokens")
        private Integer totalTokens;

        //  Timing information
        @JsonProperty("queue_time")
        private Double queueTime;
        @JsonProperty("prompt_time")
        private Double promptTime;
        @JsonProperty("completion_time")
        private Double completionTime;
        @JsonProperty("total_time")
        private Double totalTime;
    }
}