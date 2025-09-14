// src/main/java/com/example/eternaltalk/client/OpenAiClient.java
package com.example.eternaltalk.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class OpenAiClient {

    private final WebClient web;

    public OpenAiClient(@Value("${OPENAI_API_KEY}") String apiKey) {
        this.web = WebClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    /** Chat Completions 호출. system + user 메시지 → reply 텍스트 반환 */
    public String chat(String model, String systemPrompt, String userText) {
        String body = """
        {
          "model": "%s",
          "messages": [
            {"role": "system", "content": %s},
            {"role": "user", "content": %s}
          ]
        }
        """.formatted(
                escape(model),
                toJsonString(systemPrompt),
                toJsonString(userText)
        );

        return web.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, r -> r.bodyToMono(String.class)
                        .flatMap(b -> Mono.error(new IllegalArgumentException("OpenAI 호출 실패: " + b))))
                .bodyToMono(OpenAiChatResponse.class)
                .map(resp -> {
                    if (resp.choices() == null || resp.choices().length == 0
                            || resp.choices()[0].message() == null) return "";
                    return resp.choices()[0].message().content();
                })
                .block();
    }

    // ---- 응답 모델 ----
    public record OpenAiChatResponse(OpenAiChoice[] choices) {}
    public record OpenAiChoice(OpenAiMessage message) {}
    public record OpenAiMessage(String role, String content) {}

    // ---- Helpers ----
    private static String escape(String s){ return s.replace("\\","\\\\").replace("\"","\\\""); }
    private static String toJsonString(String s){
        if (s == null) s = "";
        return "\"" + escape(s).replace("\n","\\n") + "\"";
    }
}
