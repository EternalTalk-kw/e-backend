package com.example.eternaltalk.dto;

import jakarta.validation.constraints.NotBlank;

public class VideoDtos {

    // (기존) 텍스트 기반 생성 요청
    public static class GenerateRequest {
        @NotBlank(message = "text는 필수입니다.")
        public String text;
    }

    public static class GenerateResponse {
        public String jobId;
        public String status; // PENDING | PROCESSING | DONE | ERROR
        public GenerateResponse(String jobId, String status){ this.jobId = jobId; this.status = status; }
    }

    // === [추가] 오디오 URL로 바로 생성 ===
    public static class GenerateFromAudioRequest {
        @NotBlank(message = "audioUrl은 필수입니다.")
        public String audioUrl; // voice* 로직에서 생성한 mp3의 공개 URL
    }

    public static class UploadPhotoResponse {
        public String url;
        public String photoUrl;
        public UploadPhotoResponse(String url) {
            this.url = url; this.photoUrl = url;
        }
    }

    public static class StatusResponse {
        public String status;   // PENDING | PROCESSING | DONE | ERROR
        public String videoUrl; // DONE일 때만 채움
        public StatusResponse(String status, String videoUrl){ this.status = status; this.videoUrl = videoUrl; }
    }
}
