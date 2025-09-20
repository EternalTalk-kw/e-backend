package com.example.eternaltalk.dto;

import jakarta.validation.constraints.NotBlank;

public class VideoDtos {

    // POST /api/video/generate
    public static class GenerateRequest {
        @NotBlank(message = "text는 필수입니다.")
        public String text; // 한글 기준 15자(공백/이모지 제외) 제한 - 서버에서 검증/TTS 수행
    }
    public static class GenerateResponse {
        public String jobId;
        public String status; // PENDING | PROCESSING | DONE | ERROR
        public GenerateResponse(String jobId, String status){ this.jobId = jobId; this.status = status; }
    }

    // POST /api/video/upload-photo
    public static class UploadPhotoResponse {
        public String photoUrl;
        public String url;

        public UploadPhotoResponse(String url) {
            this.url = url;
            this.photoUrl = url;
        }
    }

    // GET /api/video/status/{jobId}
    public static class StatusResponse {
        public String status;   // PENDING | PROCESSING | DONE | ERROR
        public String videoUrl; // DONE일 때만 채움
        public StatusResponse(String status, String videoUrl){ this.status = status; this.videoUrl = videoUrl; }
    }
}
