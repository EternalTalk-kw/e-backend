// src/main/java/com/example/eternaltalk/controller/ChatController.java
package com.example.eternaltalk.controller;

import com.example.eternaltalk.dto.ChatDtos;
import com.example.eternaltalk.security.SecurityUtils;
import com.example.eternaltalk.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatService service;
    public ChatController(ChatService service){ this.service = service; }

    // ✅ GET /api/memory/profile 추가 (프론트 hasProfile/getProfile에서 호출)
    @GetMapping("/memory/profile")
    public ResponseEntity<ChatDtos.ProfileResponse> getProfile() {
        String email = SecurityUtils.currentUserEmailOrThrow();
        return service.getProfile(email)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // PUT /api/memory/profile
    @PutMapping("/memory/profile")
    public ChatDtos.UpsertProfileResponse upsertProfile(@Valid @RequestBody ChatDtos.UpsertProfileRequest req){
        String email = SecurityUtils.currentUserEmailOrThrow();
        return service.upsertProfile(email, req.displayName, req.personalityPrompt);
    }

    // POST /api/chat/send
    @PostMapping("/chat/send")
    public ChatDtos.SendResponse send(@Valid @RequestBody ChatDtos.SendRequest req){
        String email = SecurityUtils.currentUserEmailOrThrow();
        return service.send(email, req.text);
    }

    // GET /api/chat/quota
    @GetMapping("/chat/quota")
    public ChatDtos.QuotaResponse quota(){
        String email = SecurityUtils.currentUserEmailOrThrow();
        return service.quota(email);
    }
}
