// src/main/java/com/example/eternaltalk/controller/ChatController.java
package com.example.eternaltalk.controller;

import com.example.eternaltalk.dto.ChatDtos;
import com.example.eternaltalk.security.SecurityUtils;
import com.example.eternaltalk.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatService service;
    public ChatController(ChatService service){ this.service = service; }

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
