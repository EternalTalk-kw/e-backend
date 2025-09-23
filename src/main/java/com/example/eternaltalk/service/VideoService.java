package com.example.eternaltalk.service;

import com.example.eternaltalk.client.HeygenClient;
import com.example.eternaltalk.client.ElevenLabsClient;
import com.example.eternaltalk.domain.User;
import com.example.eternaltalk.domain.memory.MemoryProfile;
import com.example.eternaltalk.domain.video.VideoRequest;
import com.example.eternaltalk.dto.VideoDtos;
import com.example.eternaltalk.repository.MemoryProfileRepository;
import com.example.eternaltalk.repository.UserRepository;
import com.example.eternaltalk.repository.VideoRequestRepository;
import com.example.eternaltalk.storage.S3Uploader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;

@Service
public class VideoService {

    private final HeygenClient heygen;
    private final ElevenLabsClient eleven; // 텍스트→mp3 생성(voice* 로직 재사용)
    private final UserRepository userRepository;
    private final MemoryProfileRepository memoryProfileRepository;
    private final VideoRequestRepository videoRequestRepository;
    private final S3Uploader s3;

    public VideoService(
            HeygenClient heygen,
            ElevenLabsClient eleven,
            UserRepository userRepository,
            MemoryProfileRepository memoryProfileRepository,
            VideoRequestRepository videoRequestRepository,
            S3Uploader s3
    ) {
        this.heygen = heygen;
        this.eleven = eleven;
        this.userRepository = userRepository;
        this.memoryProfileRepository = memoryProfileRepository;
        this.videoRequestRepository = videoRequestRepository;
        this.s3 = s3;
    }

    /** 프론트가 텍스트만 줄 때: 서버 내부에서 TTS 후 generateFromAudio 재사용 */
    @Transactional
    public VideoDtos.GenerateResponse generateFromText(String email, String text) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // (선택) 글자수/이모지 제한 등 기존 규칙 검증
        if (!isValidShortKorean(text)) {
            throw new IllegalArgumentException("텍스트는 한글 15자(공백/이모지 제외) 이내여야 합니다.");
        }

        // 1) TTS → mp3 바이트 (ElevenLabs 사용)
        byte[] mp3 = synthesizeToMp3(text);

        // 2) mp3를 S3에 올리고 presigned URL 획득(현재 12h 유효)
        String audioKey = "voice/" + user.getId() + "/" + nowTs() + ".mp3";
        String audioUrl = s3.uploadBytes(audioKey, mp3, "audio/mpeg");

        // 3) 동일 로직 재사용
        return generateFromAudio(email, audioUrl);
    }

    /** 이미 mp3 URL이 있을 때: 사진 URL + 오디오 URL로 HeyGen에 생성 요청 */
    @Transactional
    public VideoDtos.GenerateResponse generateFromAudio(String email, String audioUrl) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        MemoryProfile profile = memoryProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("프로필이 없습니다. 먼저 /api/video/upload-photo 로 사진을 업로드하세요."));

        if (profile.getPhotoUrl() == null || profile.getPhotoUrl().isBlank()) {
            throw new IllegalStateException("사진 URL이 비어 있습니다. /api/video/upload-photo 먼저 호출하세요.");
        }

        // 1) HeyGen Avatar IV 생성(사진 1장 + 오디오 URL)
        String videoId = heygen.createAvatarIVVideo(
                profile.getPhotoUrl(),
                audioUrl,
                1280, 720, // 16:9 (무료/플랜 제한 고려)
                "EternalTalk " + nowTs()
        );

        // 2) 우리 DB에 job 기록
        VideoRequest vr = VideoRequest.builder()
                .userId(user.getId())
                .jobId(videoId)
                .status("PROCESSING")
                .photoUrl(profile.getPhotoUrl())
                .audioUrl(audioUrl)
                .build();
        videoRequestRepository.save(vr);

        return new VideoDtos.GenerateResponse(videoId, "PROCESSING");
    }

    /** 상태 조회: HeyGen status → DB 반영 → 응답 반환 */
    @Transactional
    public VideoDtos.StatusResponse status(String email, String jobId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        VideoRequest vr = videoRequestRepository.findByJobIdAndUserId(jobId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("해당 jobId가 없습니다."));

        HeygenClient.Status s = heygen.getVideoStatus(jobId);
        String st = s.status() != null ? s.status().toUpperCase() : "PENDING";

        switch (st) {
            case "COMPLETED" -> {
                vr.setStatus("DONE");
                vr.setResultUrl(s.videoUrl());
                videoRequestRepository.save(vr);
                return new VideoDtos.StatusResponse("DONE", s.videoUrl());
            }
            case "FAILED" -> {
                vr.setStatus("ERROR");
                videoRequestRepository.save(vr);
                return new VideoDtos.StatusResponse("ERROR", null);
            }
            default -> {
                if (!"PROCESSING".equals(vr.getStatus())) {
                    vr.setStatus("PROCESSING");
                    videoRequestRepository.save(vr);
                }
                return new VideoDtos.StatusResponse("PROCESSING", null);
            }
        }
    }

    /** 사진 업로드: S3 업로드 후 MemoryProfile.photoUrl 저장/갱신 */
    @Transactional
    public VideoDtos.UploadPhotoResponse uploadPhoto(String email, MultipartFile file) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        try {
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("업로드할 파일이 없습니다.");
            }

            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
            }

            // 확장자 추정
            String ext = "jpg";
            if (contentType.contains("png")) ext = "png";
            else if (contentType.contains("jpeg")) ext = "jpg";
            else if (contentType.contains("webp")) ext = "webp";

            byte[] bytes = file.getBytes();
            String key = "photo/" + user.getId() + "/" + nowTs() + "." + ext;

            // S3 업로드 + presigned URL 획득
            String photoUrl = s3.uploadBytes(key, bytes, contentType);

            // MemoryProfile 저장/갱신
            Optional<MemoryProfile> opt = memoryProfileRepository.findByUserId(user.getId());
            MemoryProfile profile = opt.orElseGet(() -> {
                MemoryProfile p = new MemoryProfile();
                p.setUserId(user.getId());
                return p;
            });
            profile.setPhotoUrl(photoUrl);
            memoryProfileRepository.save(profile);

            return new VideoDtos.UploadPhotoResponse(photoUrl);

        } catch (Exception e) {
            throw new RuntimeException("사진 업로드 실패: " + e.getMessage(), e);
        }
    }

    // === 헬퍼들 ===
    private byte[] synthesizeToMp3(String text) {
        // 기본 보이스 ID를 하나 정해줘야 함.
        // 예를 들어, 환경 변수로 VOICE_ID를 받아오거나, 프로젝트에서 공통 보이스 ID를 관리하도록 해.
        String defaultVoiceId = "EXAVITQu4vr4xnSDxMaL"; // ElevenLabs 기본 제공 보이스 중 하나 (예시)

        return eleven.tts(defaultVoiceId, text);
    }


    private boolean isValidShortKorean(String s) {
        return s != null && s.trim().length() > 0 && s.trim().length() <= 15;
    }

    private String nowTs() {
        return new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
    }
}
