package com.example.eternaltalk.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
public class HeygenClient {

    private final WebClient api;

    public HeygenClient(@Value("${HEYGEN_API_KEY}") String apiKey) {
        this.api = WebClient.builder()
                .baseUrl("https://api.heygen.com")
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-Api-Key", apiKey)
                .build();
    }

    /**
     * 사진 1장(image_url) + 오디오(audio_url)로 토킹헤드 생성.
     * 성공 시 HeyGen의 video_id 반환.
     */
    public String createAvatarIVVideo(String imageUrl, String audioUrl, Integer width, Integer height, String title) {
        Map<String, Object> payload = Map.of(
                "title", title != null ? title : "EternalTalk",
                "image_url", imageUrl,
                "voice", Map.of(
                        "type", "audio",
                        "audio_url", audioUrl
                ),
                "dimension", Map.of(
                        "width",  width != null ? width  : 1280,
                        "height", height != null ? height : 720
                )
        );

        Map<String, Object> resp = api.post()
                .uri("/v2/video/av4/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, r -> r.bodyToMono(String.class)
                        .flatMap(b -> Mono.error(new RuntimeException("HeyGen av4 generate 실패: " + b))))
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        // 응답은 { "data": { "video_id": "..." }, ... } 혹은 { "video_id": "..." } 형태가 올 수 있음
        Object data = resp != null ? resp.get("data") : null;
        Object videoId = null;

        if (data instanceof Map) {
            videoId = ((Map<?, ?>) data).get("video_id");
        }
        if (videoId == null) { // 혹시 data 없이 바로 내려오는 경우 대비
            videoId = resp != null ? resp.get("video_id") : null;
        }

        if (videoId == null) {
            throw new IllegalStateException("HeyGen 응답에 video_id 없음: " + resp);
        }
        return videoId.toString();
    }

    /** v1/video_status.get 으로 현재 상태 및 완성된 비디오 URL 조회 */
    public Status getVideoStatus(String videoId) {
        Map<String, Object> resp = api.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/video_status.get")
                        .queryParam("video_id", videoId)
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, r -> r.bodyToMono(String.class)
                        .flatMap(b -> Mono.error(new RuntimeException("HeyGen status 조회 실패: " + b))))
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        String status = null;
        String videoUrl = null;

        if (resp != null) {
            Object data = resp.get("data");
            if (data instanceof Map<?, ?> m) {
                Object st = m.get("status");
                if (st != null) status = st.toString();

                // 문서/환경에 따라 "video_url" 또는 "url" 로 올 수 있으니 둘 다 케이스 처리
                Object vu = (m.get("video_url") != null) ? m.get("video_url") : m.get("url");
                if (vu != null) videoUrl = vu.toString();
            } else {
                // 혹시 최상위에 바로 오는 경우
                Object st = resp.get("status");
                if (st != null) status = st.toString();
                Object vu = (resp.get("video_url") != null) ? resp.get("video_url") : resp.get("url");
                if (vu != null) videoUrl = vu.toString();
            }
        }

        return new Status(status, videoUrl);
    }

    public record Status(String status, String videoUrl) {}
}
