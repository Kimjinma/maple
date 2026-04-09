package com.example.maple.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private Long kakaoId;

    private String email;
    
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(length = 512)
    private String refreshToken;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    public User(Long kakaoId, String email, String nickname, Role role) {
        this.kakaoId = kakaoId;
        this.email = email;
        this.nickname = nickname;
        this.role = role != null ? role : Role.ROLE_USER;
    }

    // Refresh Token 갱신
    public void updateRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    // 커뮤니티용 이메일이나 닉네임 변경 시
    public void updateProfile(String nickname, String email) {
        if (nickname != null) this.nickname = nickname;
        if (email != null) this.email = email;
    }
}
