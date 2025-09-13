package com.example.eternaltalk.domain.memory;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "memory_profile",
        uniqueConstraints = @UniqueConstraint(name="uk_memory_profile_user", columnNames = "user_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MemoryProfile {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="user_id", nullable=false)
    private Long userId;

    @Column(name="voice_clone_id")
    private String voiceCloneId;

    @Column(name="photo_url")
    private String photoUrl;

    private LocalDateTime updatedAt;

    @PrePersist @PreUpdate
    void touch(){ this.updatedAt = LocalDateTime.now(); }
}
