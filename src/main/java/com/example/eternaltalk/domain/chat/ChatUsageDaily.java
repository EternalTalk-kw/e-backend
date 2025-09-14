// src/main/java/com/example/eternaltalk/domain/chat/ChatUsageDaily.java
package com.example.eternaltalk.domain.chat;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "chat_usage_daily",
        uniqueConstraints = @UniqueConstraint(name="uk_chat_usage_daily_user_date",
                columnNames = {"user_id","usage_date"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChatUsageDaily {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="user_id", nullable=false)
    private Long userId;

    @Column(name="usage_date", nullable=false)
    private LocalDate usageDate;

    @Column(name="used_chars", nullable=false)
    private int usedChars;
}
