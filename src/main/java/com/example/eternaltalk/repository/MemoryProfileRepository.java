package com.example.eternaltalk.repository;

import com.example.eternaltalk.domain.memory.MemoryProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemoryProfileRepository extends JpaRepository<MemoryProfile, Long> {
    Optional<MemoryProfile> findByUserId(Long userId);
}
