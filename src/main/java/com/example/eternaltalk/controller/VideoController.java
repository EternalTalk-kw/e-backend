package com.example.eternaltalk.controller;

import com.example.eternaltalk.dto.VideoDtos;
import com.example.eternaltalk.security.SecurityUtils;
import com.example.eternaltalk.service.VideoService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/video")
public class VideoController {

    private final VideoService service;
    public VideoController(VideoService service){ this.service = service; }

    // POST /api/video/generate (입력 {text} → 서버가 TTS 후 D-ID 요청)
    @PostMapping("/generate")
    public VideoDtos.GenerateResponse generate(@Valid @RequestBody VideoDtos.GenerateRequest req){
        String email = SecurityUtils.currentUserEmailOrThrow();
        return service.generate(email, req.text);
    }

    // POST /api/video/upload-photo (정면 사진 업로드 → URL 반환 및 memory_profile.photo_url 갱신)
    @PostMapping(value="/upload-photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VideoDtos.UploadPhotoResponse uploadPhoto(@RequestPart("file") MultipartFile file){
        String email = SecurityUtils.currentUserEmailOrThrow();
        return service.uploadPhoto(email, file);
    }

    // GET /api/video/status/{jobId}
    @GetMapping("/status/{jobId}")
    public VideoDtos.StatusResponse status(@PathVariable String jobId){
        String email = SecurityUtils.currentUserEmailOrThrow();
        return service.status(email, jobId);
    }
}
