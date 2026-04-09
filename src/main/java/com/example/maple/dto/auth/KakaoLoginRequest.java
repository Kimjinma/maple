package com.example.maple.dto.auth;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class KakaoLoginRequest {
    // 프론트엔드가 카카오에서 발급받은 유저의 액세스 토큰
    private String kakaoAccessToken;
}
