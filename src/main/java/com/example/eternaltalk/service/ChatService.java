// src/main/java/com/example/eternaltalk/service/ChatService.java
package com.example.eternaltalk.service;

import com.example.eternaltalk.client.OpenAiClient;
import com.example.eternaltalk.domain.User;
import com.example.eternaltalk.domain.chat.ChatUsageDaily;
import com.example.eternaltalk.domain.memory.MemoryProfile;
import com.example.eternaltalk.dto.ChatDtos;
import com.example.eternaltalk.repository.ChatUsageDailyRepository;
import com.example.eternaltalk.repository.MemoryProfileRepository;
import com.example.eternaltalk.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Optional;

@Service
public class ChatService {

    private final UserRepository userRepository;
    private final MemoryProfileRepository memoryProfileRepository;
    private final ChatUsageDailyRepository chatUsageDailyRepository;
    private final OpenAiClient openAi;

    // 기본 모델 (필요하면 설정값으로 변경)
    private static final String DEFAULT_MODEL = "gpt-4o-mini";

    public ChatService(UserRepository userRepository,
                       MemoryProfileRepository memoryProfileRepository,
                       ChatUsageDailyRepository chatUsageDailyRepository,
                       OpenAiClient openAi) {
        this.userRepository = userRepository;
        this.memoryProfileRepository = memoryProfileRepository;
        this.chatUsageDailyRepository = chatUsageDailyRepository;
        this.openAi = openAi;
    }

    // 1) 프로필 업서트
    public ChatDtos.UpsertProfileResponse upsertProfile(String email, String displayName, String personalityPrompt){
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        MemoryProfile mp = memoryProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> MemoryProfile.builder().userId(user.getId()).build());
        mp.setDisplayName(displayName);
        mp.setPersonalityPrompt(personalityPrompt);
        memoryProfileRepository.save(mp);

        return new ChatDtos.UpsertProfileResponse(mp.getDisplayName(), mp.getPersonalityPrompt());
    }

    // 2) 채팅 전송 (일일 글자 제한 검사)
    public ChatDtos.SendResponse send(String email, String userText){
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        Plan plan = resolvePlan(user); // FREE/SILVER/GOLD
        int limit = plan.dailyLimit;

        int todayUsed = chatUsageDailyRepository.findByUserIdAndUsageDate(user.getId(), LocalDate.now())
                .map(ChatUsageDaily::getUsedChars).orElse(0);

        int inputCount = countCharacters(userText); // 기본: 공백 제외 코드포인트 수
        if (todayUsed + inputCount > limit) {
            int remaining = Math.max(0, limit - todayUsed);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "일일 입력 글자 제한 초과 (남은 글자: " + remaining + ", 요금제: " + plan.name() + ")");
        }

        // 시스템 프롬프트 구성: displayName + personalityPrompt
        MemoryProfile mp = memoryProfileRepository.findByUserId(user.getId())
                .orElse(null);

        String systemPrompt = buildSystemPrompt(mp);

        // OpenAI 호출
        String reply = openAi.chat(DEFAULT_MODEL, systemPrompt, userText);

        // 사용량 반영
        ChatUsageDaily row = chatUsageDailyRepository.findByUserIdAndUsageDate(user.getId(), LocalDate.now())
                .orElseGet(() -> ChatUsageDaily.builder()
                        .userId(user.getId())
                        .usageDate(LocalDate.now())
                        .usedChars(0)
                        .build());
        row.setUsedChars(row.getUsedChars() + inputCount);
        chatUsageDailyRepository.save(row);

        int remaining = Math.max(0, limit - row.getUsedChars());
        return new ChatDtos.SendResponse(reply, remaining);
    }

    // 3) 잔여 쿼터 조회
    public ChatDtos.QuotaResponse quota(String email){
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        Plan plan = resolvePlan(user);
        int used = chatUsageDailyRepository.findByUserIdAndUsageDate(user.getId(), LocalDate.now())
                .map(ChatUsageDaily::getUsedChars).orElse(0);
        int remaining = Math.max(0, plan.dailyLimit - used);
        return new ChatDtos.QuotaResponse(remaining, plan.dailyLimit);
    }

    public Optional<ChatDtos.ProfileResponse> getProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + email));

        return memoryProfileRepository.findByUserId(user.getId())
                .map(mp -> new ChatDtos.ProfileResponse(
                        mp.getId(),
                        mp.getDisplayName(),
                        mp.getPersonalityPrompt(),
                        mp.getPhotoUrl(),// 엔티티에 없다면 null 리턴
                        mp.getVoiceCloneId()
                ));
    }


    // ---- Helpers ----

    /** FREE=100, SILVER=500, GOLD=700 */
    private enum Plan {
        FREE(100), SILVER(500), GOLD(700);
        final int dailyLimit; Plan(int v){ this.dailyLimit = v; }
        static Plan safe(String s){
            try { return Plan.valueOf(s.toUpperCase()); } catch (Exception e){ return FREE; }
        }
    }

    /** User 엔티티에 plan/membership/tier 중 존재하는 필드를 찾아 사용. 없으면 FREE */
    private Plan resolvePlan(User user){
        for (String fieldName : new String[]{"plan", "membership", "tier"}) {
            try {
                Field f = user.getClass().getDeclaredField(fieldName);
                f.setAccessible(true);
                Object v = f.get(user);
                if (v != null) return Plan.safe(v.toString());
            } catch (Exception ignore) {}
        }
        return Plan.FREE;
    }

    /** 공백 제외 글자수(코드포인트) 카운트 */
    private int countCharacters(String text){
        if (text == null) return 0;
        int i = 0, n = text.length(), c = 0;
        while (i < n){
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isWhitespace(cp)) continue; // 공백 제외
            c++;
        }
        return c;
    }

    private String buildSystemPrompt(MemoryProfile mp){
        String name = (mp != null && mp.getDisplayName() != null) ? mp.getDisplayName().trim() : "고인";
        String persona = (mp != null && mp.getPersonalityPrompt() != null) ? mp.getPersonalityPrompt().trim() : "";
        return """
        당신은 '%s'의 말투와 성격을 반영해 대화합니다.
        - 인격 설명: %s
        - 사용자에게 위로와 공감을 우선합니다.
        - 과장 없이 담백하고 진심 어린 어조를 유지합니다.
        """.formatted(name, persona.isEmpty() ? "설정 없음" : persona);
    }
}
