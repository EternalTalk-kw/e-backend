package com.example.eternaltalk.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="users")//프젝 시작
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false, unique=true) private String email;
    @Column(nullable=false) private String passwordHash;
    @Column(nullable=false) private String nickname;

    @Enumerated(EnumType.STRING) @Column(nullable=false)
    private AuthProvider authProvider = AuthProvider.LOCAL;

    @Enumerated(EnumType.STRING) @Column(nullable=false)
    private Role role = Role.USER;

    private Boolean consent = false;           // 디지털 유산 동의여부
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;

    @PrePersist void prePersist(){ this.createdAt = LocalDateTime.now(); }
}
