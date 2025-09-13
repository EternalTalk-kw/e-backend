package com.example.eternaltalk.dto;

import jakarta.validation.constraints.NotBlank;

public class VoiceDtos {

    public static class GenerateRequest {
        @NotBlank(message = "text는 필수입니다.")
        public String text;
    }

    public static class AudioResponse {
        public String audioUrl;
        public AudioResponse(String audioUrl) { this.audioUrl = audioUrl; }
    }

    public static class UploadSampleResponse {
        public String voiceId;
        public UploadSampleResponse(String voiceId) { this.voiceId = voiceId; }
    }

    public static class VoiceSample {
        public String sampleId;
        public String url;
        public VoiceSample(String sampleId, String url) {
            this.sampleId = sampleId; this.url = url;
        }
    }
}
