package com.example.eternaltalk.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class ElevenLabsClient {

    private final WebClient web;

    public ElevenLabsClient(@Value("${ELEVENLABS_API_KEY}") String apiKey) {
        this.web = WebClient.builder()
                .baseUrl("https://api.elevenlabs.io")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
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

    // 샘플 업로드(간단 버전): 새 보이스 생성/갱신 → voice_id 반환
    public String createOrUpdateVoice(Long userId, byte[] sample, String filename){
        LinkedMultiValueMap<String,Object> form = new LinkedMultiValueMap<>();
        form.add("name", "user-" + userId);
        form.add("sample", new ByteArrayResource(sample){
            @Override public String getFilename(){ return filename != null ? filename : "sample.mp3"; }
        });

        return web.post()
                .uri("/v1/voices/add")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(form))
                .retrieve()
                .onStatus(HttpStatusCode::isError, r -> r.bodyToMono(String.class)
                        .flatMap(b -> Mono.error(new IllegalArgumentException("보이스 업로드 실패: " + b))))
                .bodyToMono(VoiceCreateResponse.class)
                .map(VoiceCreateResponse::voice_id)
                .block();
    }

    public record VoiceCreateResponse(String voice_id){}
}
