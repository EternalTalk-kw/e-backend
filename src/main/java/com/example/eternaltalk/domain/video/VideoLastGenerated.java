package com.example.eternaltalk.domain.video;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "video_last_generated", uniqueConstraints =
@UniqueConstraint(name="uk_video_last_generated_user", columnNames = "user_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VideoLastGenerated {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="user_id", nullable=false)
    private Long userId;

    @Column(name="last_generated_at", nullable=false)
    private LocalDateTime lastGeneratedAt;
}
