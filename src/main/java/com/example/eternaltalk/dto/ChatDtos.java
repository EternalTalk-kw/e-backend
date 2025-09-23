// src/main/java/com/example/eternaltalk/dto/ChatDtos.java
package com.example.eternaltalk.dto;

import jakarta.validation.constraints.NotBlank;

public class ChatDtos {

    // PUT /api/memory/profile
    public static class UpsertProfileRequest {
        @NotBlank(message = "displayName은 필수입니다.")
        public String displayName;
        @NotBlank(message = "personalityPrompt는 필수입니다.")
        public String personalityPrompt;
    }
    public static class UpsertProfileResponse {
        public String displayName;
        public String personalityPrompt;
        public UpsertProfileResponse(String n, String p){ this.displayName = n; this.personalityPrompt = p; }
    }

    // POST /api/chat/send
    public static class SendRequest {
        @NotBlank(message = "text는 필수입니다.")
        public String text;
    }
    public static class SendResponse {
        public String reply;
        public int remainingCharsToday;
        public SendResponse(String reply, int remaining){ this.reply = reply; this.remainingCharsToday = remaining; }
    }

    // GET /api/chat/quota
    public static class QuotaResponse {
        public int remainingCharsToday;
        public int planLimit;
        public QuotaResponse(int remaining, int planLimit){ this.remainingCharsToday = remaining; this.planLimit = planLimit; }
    }

    public record ProfileResponse(
            Long profileId,
            String displayName,
            String personalityPrompt,
            String photoUrl, // 없으면 null 허용
            String voiceCloneId
    ) {}
}
