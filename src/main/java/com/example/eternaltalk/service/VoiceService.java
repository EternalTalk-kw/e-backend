package com.example.eternaltalk.service;

import com.example.eternaltalk.client.ElevenLabsClient;
import com.example.eternaltalk.domain.User;
import com.example.eternaltalk.domain.memory.MemoryProfile;
import com.example.eternaltalk.dto.VoiceDtos;
import com.example.eternaltalk.repository.MemoryProfileRepository;
import com.example.eternaltalk.repository.UserRepository;
import com.example.eternaltalk.storage.S3Uploader;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class VoiceService {

    private final UserRepository userRepository;
    private final MemoryProfileRepository memoryProfileRepository;
    private final ElevenLabsClient eleven;
    private final S3Uploader s3;

    public VoiceService(UserRepository userRepository,
                        MemoryProfileRepository memoryProfileRepository,
                        ElevenLabsClient eleven,
                        S3Uploader s3) {
        this.userRepository = userRepository;
        this.memoryProfileRepository = memoryProfileRepository;
        this.eleven = eleven;
        this.s3 = s3;
    }

    // POST /api/voice/generate
    public String generate(String email, String text){
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 서비스 레벨 요금제/쿼터 규칙 검사 훅 (영상에서만 적용 → 현재 패스)
        // checkPlanRules(user);

        MemoryProfile mp = memoryProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("메모리 프로필이 없습니다. 먼저 프로필을 생성해 주세요."));

        if (isBlank(mp.getVoiceCloneId())) {
            throw new IllegalArgumentException("voice_id가 없습니다. 먼저 샘플을 등록해 주세요.");
        }

        if (!isValidKoreanUnderLimit(text, 15)) {
            throw new IllegalArgumentException("text는 한글 기준 공백/이모지 제외 15자 이내여야 합니다.");
        }

        byte[] mp3 = eleven.tts(mp.getVoiceCloneId(), text);
        String key = buildS3Key(user.getId());
        return s3.uploadBytes(key, mp3, "audio/mpeg"); // { audioUrl }
    }

    // POST /api/voice/upload-sample
    public String uploadSample(String email, MultipartFile file){
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (file == null || file.isEmpty()) throw new IllegalArgumentException("샘플 오디오 파일이 필요합니다.");
        byte[] bytes;
        try { bytes = file.getBytes(); }
        catch (Exception e){ throw new IllegalArgumentException("파일 읽기에 실패했습니다."); }

        String voiceId = eleven.createOrUpdateVoice(user.getId(), bytes, file.getOriginalFilename());

        MemoryProfile mp = memoryProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> MemoryProfile.builder().userId(user.getId()).build());
        mp.setVoiceCloneId(voiceId);
        memoryProfileRepository.save(mp);
        return voiceId;
    }

    // GET /api/voice/samples (테스트용)
    public List<VoiceDtos.VoiceSample> listMySamples(String email){
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        Optional<MemoryProfile> omp = memoryProfileRepository.findByUserId(user.getId());
        if (omp.isEmpty() || isBlank(omp.get().getVoiceCloneId())) return List.of();
        return List.of(new VoiceDtos.VoiceSample(
                "sample-" + omp.get().getVoiceCloneId(),
                "https://example.com/voices/" + omp.get().getVoiceCloneId() + "/sample"
        ));
    }

    private String buildS3Key(Long userId){
        String ts = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        return "voices/" + userId + "/" + ts + ".mp3";
    }
    private boolean isBlank(String s){ return s == null || s.isBlank(); }

    // 한글 기준 15자 이내(공백/이모지 제외)
    public static boolean isValidKoreanUnderLimit(String input, int limit){
        if (input == null) return false;
        int count = 0, i = 0, n = input.length();
        while (i < n){
            int cp = input.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isWhitespace(cp)) continue;     // 공백 제외
            if (isEmoji(cp)) continue;                     // 이모지 제외

            Character.UnicodeBlock b = Character.UnicodeBlock.of(cp);
            if (b == Character.UnicodeBlock.HANGUL_SYLLABLES
                    || b == Character.UnicodeBlock.HANGUL_JAMO
                    || b == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO
                    || b == Character.UnicodeBlock.HANGUL_JAMO_EXTENDED_A
                    || b == Character.UnicodeBlock.HANGUL_JAMO_EXTENDED_B) {
                count++;
                if (count > limit) return false;
            }
        }
        return count >= 1 && count <= limit;
    }
    private static boolean isEmoji(int cp){
        return (cp >= 0x1F300 && cp <= 0x1FAFF)
                || (cp >= 0x2600 && cp <= 0x27BF)
                || (cp >= 0xFE00 && cp <= 0xFE0F)
                || (cp >= 0x1F1E6 && cp <= 0x1F1FF);
    }

    // 향후: 영상 간격/챗봇 글자 수 등 요금제 규칙 검사 위치
    // private void checkPlanRules(User user) { ... }
}
