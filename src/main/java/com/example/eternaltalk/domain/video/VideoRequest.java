package com.example.eternaltalk.domain.video;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "video_request", indexes = {
        @Index(name="idx_video_request_user", columnList = "user_id"),
        @Index(name="idx_video_request_job", columnList = "job_id", unique = true)
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VideoRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="user_id", nullable=false)
    private Long userId;

    @Column(name="job_id", nullable=false, unique = true)
    private String jobId;

    @Column(name="status", nullable=false) // PENDING, PROCESSING, DONE, ERROR
    private String status;

    @Column(name="photo_url") // 요청 당시 기준
    private String photoUrl;

    @Column(name="audio_url")
    private String audioUrl;

    @Column(name="result_url")
    private String resultUrl;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist void prePersist(){ createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void preUpdate(){ updatedAt = LocalDateTime.now(); }
}
