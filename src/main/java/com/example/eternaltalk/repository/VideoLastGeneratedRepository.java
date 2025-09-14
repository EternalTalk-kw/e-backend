package com.example.eternaltalk.repository;

import com.example.eternaltalk.domain.video.VideoLastGenerated;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VideoLastGeneratedRepository extends JpaRepository<VideoLastGenerated, Long> {
    Optional<VideoLastGenerated> findByUserId(Long userId);
}
