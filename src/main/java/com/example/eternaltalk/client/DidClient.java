package com.example.eternaltalk.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * D-ID Talks API thin client.
 * - POST /v1/talks         -> create (returns id/status)
 * - GET  /v1/talks/{id}    -> status (status/result_url)
 *
 * ⚠ 인증 헤더는 계정 설정에 따라 Bearer/Basic/x-api-key 등 다를 수 있음.
 *   필요 시 아래 defaultHeader 를 맞춰주세요.
 */
@Component
public class DidClient {

    private final WebClient web;

    public DidClient(@Value("${DID_API_KEY}") String apiKey) {
        this.web = WebClient.builder()
                .baseUrl("https://api.d-id.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    /** Create talk: returns talk id + status ("created" | "started" | ...) */
    public CreateResponse createTalk(String sourceUrl, String audioUrl) {
        String body = """
            { "source_url": "%s", "audio_url": "%s" }
        """.formatted(escape(sourceUrl), escape(audioUrl));

        return web.post()
                .uri("/v1/talks")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, r -> r.bodyToMono(String.class)
                        .flatMap(b -> Mono.error(new IllegalArgumentException("D-ID 생성 실패: " + b))))
                .bodyToMono(CreateResponse.class)
                .block();
    }

    /** Get talk status; "done" 시 result_url 포함 */
    public StatusResponse getTalkStatus(String talkId) {
        return web.get()
                .uri("/v1/talks/{id}", talkId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, r -> r.bodyToMono(String.class)
                        .flatMap(b -> Mono.error(new IllegalArgumentException("D-ID 조회 실패: " + b))))
                .bodyToMono(StatusResponse.class)
                .block();
    }

    public record CreateResponse(String id, String status) {}
    public record StatusResponse(String id, String status, String result_url) {}

    private static String escape(String s){
        return s.replace("\\","\\\\").replace("\"","\\\"");
    }
}
