package com.example.maple.controller;

import com.example.maple.dto.auth.KakaoLoginRequest;
import com.example.maple.dto.auth.LoginResponse;
import com.example.maple.service.KakaoAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final KakaoAuthService kakaoAuthService;

    /**
     * 프론트엔드에서 카카오 Access Token을 받아 서버로 넘겨 로그인 처리 및 자체 토큰 발급
     */
    @PostMapping("/kakao/login")
    public ResponseEntity<LoginResponse> kakaoLogin(@RequestBody KakaoLoginRequest request) {
        LoginResponse response = kakaoAuthService.kakaoLogin(request);
        return ResponseEntity.ok(response);
    }
}
