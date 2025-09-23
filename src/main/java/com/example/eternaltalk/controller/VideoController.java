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

    // (기존) 텍스트로 생성: 서버 내부에서 TTS -> 오디오 -> 영상
    @PostMapping("/generate")
    public VideoDtos.GenerateResponse generate(@Valid @RequestBody VideoDtos.GenerateRequest req){
        String email = SecurityUtils.currentUserEmailOrThrow();
        return service.generateFromText(email, req.text);
    }

    // === [추가] 이미 만들어진 오디오(audioUrl)로 바로 생성 ===
    @PostMapping("/generate-from-audio")
    public VideoDtos.GenerateResponse generateFromAudio(@Valid @RequestBody VideoDtos.GenerateFromAudioRequest req){
        String email = SecurityUtils.currentUserEmailOrThrow();
        return service.generateFromAudio(email, req.audioUrl);
    }

    // (기존) 정면 사진 업로드
    @PostMapping(value="/upload-photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VideoDtos.UploadPhotoResponse uploadPhoto(@RequestPart("file") MultipartFile file){
        String email = SecurityUtils.currentUserEmailOrThrow();
        return service.uploadPhoto(email, file);
    }

    // (기존) 상태 폴링
    @GetMapping("/status/{jobId}")
    public VideoDtos.StatusResponse status(@PathVariable String jobId){
        String email = SecurityUtils.currentUserEmailOrThrow();
        return service.status(email, jobId);
    }
}
