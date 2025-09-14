// src/main/java/com/example/eternaltalk/repository/ChatUsageDailyRepository.java
package com.example.eternaltalk.repository;

import com.example.eternaltalk.domain.chat.ChatUsageDaily;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface ChatUsageDailyRepository extends JpaRepository<ChatUsageDaily, Long> {
    Optional<ChatUsageDaily> findByUserIdAndUsageDate(Long userId, LocalDate usageDate);
}
