package com.example.eternaltalk.service;

import com.example.eternaltalk.client.DidClient;
import com.example.eternaltalk.client.ElevenLabsClient;
import com.example.eternaltalk.domain.User;
import com.example.eternaltalk.domain.memory.MemoryProfile;
import com.example.eternaltalk.domain.video.VideoLastGenerated;
import com.example.eternaltalk.domain.video.VideoRequest;
import com.example.eternaltalk.dto.VideoDtos;
import com.example.eternaltalk.repository.MemoryProfileRepository;
import com.example.eternaltalk.repository.UserRepository;
import com.example.eternaltalk.repository.VideoLastGeneratedRepository;
import com.example.eternaltalk.repository.VideoRequestRepository;
import com.example.eternaltalk.storage.S3Uploader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class VideoService {

    private final UserRepository userRepository;
    private final MemoryProfileRepository memoryProfileRepository;
    private final VideoRequestRepository videoRequestRepository;
    private final VideoLastGeneratedRepository videoLastGeneratedRepository;
    private final ElevenLabsClient eleven;
    private final DidClient did;
    private final S3Uploader s3;

    public VideoService(UserRepository userRepository,
                        MemoryProfileRepository memoryProfileRepository,
                        VideoRequestRepository videoRequestRepository,
                        VideoLastGeneratedRepository videoLastGeneratedRepository,
                        ElevenLabsClient eleven,
                        DidClient did,
                        S3Uploader s3) {
        this.userRepository = userRepository;
        this.memoryProfileRepository = memoryProfileRepository;
        this.videoRequestRepository = videoRequestRepository;
        this.videoLastGeneratedRepository = videoLastGeneratedRepository;
        this.eleven = eleven;
        this.did = did;
        this.s3 = s3;
    }

    // === 1) Generate ===
    public VideoDtos.GenerateResponse generate(String email, String text) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        MemoryProfile mp = memoryProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("메모리 프로필이 없습니다. 먼저 프로필을 생성해 주세요."));

        if (isBlank(mp.getVoiceCloneId()) || isBlank(mp.getPhotoUrl())) {
            throw new IllegalArgumentException("voice_id 또는 photo_url이 없습니다. 먼저 샘플/사진을 등록해 주세요.");
        }

        // 요금제 규칙 검사 (FREE=3일, SILVER=2일, GOLD=1일)
        PlanType plan = resolvePlan(user); // 기본 FREE
        int needDays = plan.intervalDays;
        int remain = daysRemaining(user.getId(), needDays);
        if (remain > 0) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "영상 생성까지 " + remain + "일 남았습니다. (요금제: " + plan.name() + ")");
        }

        // 텍스트 검증: 한글 15자(공백/이모지 제외)
        if (!VoiceService.isValidKoreanUnderLimit(text, 15)) {
            throw new IllegalArgumentException("text는 한글 기준 공백/이모지 제외 15자 이내여야 합니다.");
        }

        // 1) TTS → mp3 S3 업로드
        byte[] mp3 = eleven.tts(mp.getVoiceCloneId(), text);
        String audioKey = "voices/" + user.getId() + "/" + nowTs() + ".mp3";
        String audioUrl = s3.uploadBytes(audioKey, mp3, "audio/mpeg");

        // 2) D-ID talks 호출
        DidClient.CreateResponse created = did.createTalk(mp.getPhotoUrl(), audioUrl);

        // 3) DB 기록 (비동기 잡)
        String jobId = created.id();
        String mapped = mapStatus(created.status()); // created -> PENDING 등
        videoRequestRepository.save(VideoRequest.builder()
                .userId(user.getId())
                .jobId(jobId)
                .status(mapped)
                .photoUrl(mp.getPhotoUrl())
                .audioUrl(audioUrl)
                .build());

        return new VideoDtos.GenerateResponse(jobId, mapped);
    }

    // === 2) Upload photo ===
    public VideoDtos.UploadPhotoResponse uploadPhoto(String email, MultipartFile image) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("정면 사진 파일이 필요합니다.");
        }
        String contentType = Optional.ofNullable(image.getContentType()).orElse("image/jpeg");
        if (!contentType.startsWith("image/")) {
            throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
        }

        byte[] bytes;
        try { bytes = image.getBytes(); }
        catch (Exception e){ throw new IllegalArgumentException("이미지 읽기에 실패했습니다."); }

        String ext = guessExt(contentType, image.getOriginalFilename());
        String key = "photos/" + user.getId() + "/" + nowTs() + "." + ext;
        String photoUrl = s3.uploadBytes(key, bytes, contentType);

        // memory_profile.photo_url 갱신
        MemoryProfile mp = memoryProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> com.example.eternaltalk.domain.memory.MemoryProfile.builder()
                        .userId(user.getId()).build());
        mp.setPhotoUrl(photoUrl);
        memoryProfileRepository.save(mp);

        return new VideoDtos.UploadPhotoResponse(photoUrl);
    }

    // === 3) Status ===
    public VideoDtos.StatusResponse status(String email, String jobId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        VideoRequest req = videoRequestRepository.findByJobIdAndUserId(jobId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("해당 jobId를 찾을 수 없습니다."));

        var st = did.getTalkStatus(jobId);
        String mapped = mapStatus(st.status());
        String resultUrl = ("DONE".equals(mapped)) ? st.result_url() : null;

        // DB 업데이트
        boolean changed = !Objects.equals(req.getStatus(), mapped) || !Objects.equals(req.getResultUrl(), resultUrl);
        if (changed) {
            req.setStatus(mapped);
            req.setResultUrl(resultUrl);
            videoRequestRepository.save(req);
        }

        // DONE 최초 달성 시 last_generated 갱신
        if ("DONE".equals(mapped)) {
            videoLastGeneratedRepository.findByUserId(user.getId())
                    .ifPresentOrElse(
                            v -> { v.setLastGeneratedAt(LocalDateTime.now()); videoLastGeneratedRepository.save(v); },
                            () -> videoLastGeneratedRepository.save(
                                    VideoLastGenerated.builder().userId(user.getId()).lastGeneratedAt(LocalDateTime.now()).build()
                            )
                    );
        }

        return new VideoDtos.StatusResponse(mapped, resultUrl);
    }

    // ===== Helpers =====
    private String mapStatus(String did){
        if (did == null) return "PENDING";
        return switch (did.toLowerCase()) {
            case "created" -> "PENDING";
            case "started", "processing", "in_progress" -> "PROCESSING";
            case "done", "completed" -> "DONE";
            case "error", "failed" -> "ERROR";
            default -> "PENDING";
        };
    }

    private enum PlanType {
        FREE(3), SILVER(2), GOLD(1);
        final int intervalDays; PlanType(int d){ this.intervalDays = d; }
        static PlanType safe(String s){
            try { return PlanType.valueOf(s.toUpperCase()); } catch (Exception e){ return FREE; }
        }
    }

    /** 유저 객체에 plan/membership/tier 필드가 있다면 반영, 없으면 FREE */
    private PlanType resolvePlan(User user){
        for (String fieldName : new String[]{"plan", "membership", "tier"}) {
            try {
                Field f = user.getClass().getDeclaredField(fieldName);
                f.setAccessible(true);
                Object v = f.get(user);
                if (v != null) return PlanType.safe(v.toString());
            } catch (Exception ignore) {}
        }
        return PlanType.FREE;
    }

    /** 남은 일수(양수면 제한 미충족) */
    private int daysRemaining(Long userId, int needDays){
        var lastOpt = videoLastGeneratedRepository.findByUserId(userId);
        if (lastOpt.isEmpty()) return 0;
        LocalDateTime last = lastOpt.get().getLastGeneratedAt();
        long diff = Duration.between(last, LocalDateTime.now()).toDays();
        long remain = needDays - diff;
        return (int) Math.max(0, remain);
    }

    private String nowTs(){
        return new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
    }
    private String guessExt(String contentType, String original){
        if (contentType != null && contentType.contains("png")) return "png";
        if (contentType != null && contentType.contains("webp")) return "webp";
        if (original != null && original.toLowerCase().endsWith(".png")) return "png";
        if (original != null && original.toLowerCase().endsWith(".webp")) return "webp";
        return "jpg";
    }
    private boolean isBlank(String s){ return s == null || s.isBlank(); }
}
