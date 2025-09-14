package com.example.eternaltalk.repository;

import com.example.eternaltalk.domain.video.VideoRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VideoRequestRepository extends JpaRepository<VideoRequest, Long> {
    Optional<VideoRequest> findByJobId(String jobId);
    Optional<VideoRequest> findByJobIdAndUserId(String jobId, Long userId);
}
