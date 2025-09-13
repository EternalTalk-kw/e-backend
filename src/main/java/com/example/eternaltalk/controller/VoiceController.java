package com.example.eternaltalk.controller;

import com.example.eternaltalk.dto.VoiceDtos;
import com.example.eternaltalk.security.SecurityUtils;
import com.example.eternaltalk.service.VoiceService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/voice")
public class VoiceController {

    private final VoiceService voiceService;

    public VoiceController(VoiceService voiceService) {
        this.voiceService = voiceService;
    }

    // POST /api/voice/generate
    @PostMapping("/generate")
    public VoiceDtos.AudioResponse generate(@Valid @RequestBody VoiceDtos.GenerateRequest req){
        String email = SecurityUtils.currentUserEmailOrThrow();
        String url = voiceService.generate(email, req.text);
        return new VoiceDtos.AudioResponse(url);
    }

    // POST /api/voice/upload-sample
    @PostMapping(value="/upload-sample", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VoiceDtos.UploadSampleResponse upload(@RequestPart("file") MultipartFile file){
        String email = SecurityUtils.currentUserEmailOrThrow();
        String voiceId = voiceService.uploadSample(email, file);
        return new VoiceDtos.UploadSampleResponse(voiceId);
    }

    // GET /api/voice/samples
    @GetMapping("/samples")
    public List<VoiceDtos.VoiceSample> samples(){
        String email = SecurityUtils.currentUserEmailOrThrow();
        return voiceService.listMySamples(email);
    }
}
