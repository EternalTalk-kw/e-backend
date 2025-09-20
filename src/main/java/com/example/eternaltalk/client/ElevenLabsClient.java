package com.example.eternaltalk.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class ElevenLabsClient {

    private final WebClient web;

    public ElevenLabsClient(@Value("${ELEVENLABS_API_KEY}") String apiKey) {
        this.web = WebClient.builder()
                .baseUrl("https://api.elevenlabs.io")
                .defaultHeader("xi-api-key", apiKey) // ✅ ElevenLabs는 Bearer가 아니라 이 헤더
                .build();
    }

    // 텍스트 → 음성 (mp3 바이트)
    public byte[] tts(String voiceId, String text){
        String payload = """
        {
          "text": "%s",
          "voice_settings": { "stability": 0.5, "similarity_boost": 0.75 },
          "output_format": "mp3_44100_128"
        }
        """.formatted(text.replace("\\","\\\\").replace("\"","\\\""));

        return web.post()
                .uri("/v1/text-to-speech/{voiceId}", voiceId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.valueOf("audio/mpeg"))
                .bodyValue(payload)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, r -> r.bodyToMono(String.class)
                        .flatMap(b -> Mono.error(new IllegalArgumentException("TTS 요청 실패: " + b))))
                .onStatus(HttpStatusCode::is5xxServerError, r -> r.bodyToMono(String.class)
                        .flatMap(b -> Mono.error(new RuntimeException("TTS 서버 오류: " + b))))
                .bodyToMono(byte[].class)
                .block();
    }

    /**
     * 샘플 업로드: 새 보이스 생성 → voice_id 반환
     * ⚠️ ElevenLabs는 멀티파트 필드명이 반드시 "files" (복수형) 이어야 함.
     */
    public String createOrUpdateVoice(Long userId, byte[] sample, String filename){
        // 파일 파트(파일명 보존용 ByteArrayResource)
        ByteArrayResource fileResource = new ByteArrayResource(sample) {
            @Override public String getFilename() {
                return (filename != null && !filename.isBlank()) ? filename : "sample.mp3";
            }
        };

        // 파일 파트 전용 헤더 (Content-Disposition에 name=files, filename 지정)
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM); // 또는 MediaType.valueOf("audio/mpeg")
        ContentDisposition cd = ContentDisposition
                .formData()
                .name("files") // ✅ 핵심: 필드명은 files
                .filename(fileResource.getFilename())
                .build();
        fileHeaders.setContentDisposition(cd);

        HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(fileResource, fileHeaders);

        // 멀티파트 바디 구성
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("files", filePart);                    // ✅ 반드시 "files"
        form.add("name", "user-" + userId);            // 옵션: 보이스 이름

        return web.post()
                .uri("/v1/voices/add")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(form))
                .retrieve()
                .onStatus(HttpStatusCode::isError, r -> r.bodyToMono(String.class)
                        .flatMap(b -> Mono.error(new IllegalArgumentException("보이스 업로드 실패: " + b))))
                .bodyToMono(VoiceCreateResponse.class)
                .map(resp -> {
                    if (resp == null || resp.voice_id == null || resp.voice_id.isBlank()) {
                        throw new IllegalArgumentException("보이스 업로드 실패: 응답에 voice_id 없음");
                    }
                    return resp.voice_id;
                })
                .block();
    }

    // ElevenLabs /v1/voices/add 응답의 핵심 필드만 매핑
    public static final class VoiceCreateResponse {
        public String voice_id;
    }
}
