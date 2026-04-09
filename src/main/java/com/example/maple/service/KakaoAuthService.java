package com.example.maple.service;

import com.example.maple.domain.Role;
import com.example.maple.domain.User;
import com.example.maple.dto.auth.KakaoLoginRequest;
import com.example.maple.dto.auth.KakaoUserInfoResponse;
import com.example.maple.dto.auth.LoginResponse;
import com.example.maple.repository.UserRepository;
import com.example.maple.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoAuthService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String KAKAO_USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";

    @Transactional
    public LoginResponse kakaoLogin(KakaoLoginRequest request) {
        // 1. 프론트에서 넘어온 Access Token으로 카카오 서버에 유저 정보 요청
        KakaoUserInfoResponse userInfo = getKakaoUserInfo(request.getKakaoAccessToken());

        // 2. 카카오 회원번호로 DB에서 유저 조회, 없으면 생성(가입)
        User user = userRepository.findByKakaoId(userInfo.getId())
                .orElseGet(() -> createUser(userInfo));

        // *참고: 카카오 계정의 이메일이나 닉네임이 바뀌었다면 DB 동기화 처리
        syncUserProfile(user, userInfo);

        // 3. 서비스용 JWT Access Token, Refresh Token 생성 (username으로 DB PK 사용)
        String userId = String.valueOf(user.getId());
        String accessToken = jwtTokenProvider.createAccessToken(userId);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId);

        // 4. 리프레시 토큰 DB에 저장
        user.updateRefreshToken(refreshToken);

        return new LoginResponse(
                accessToken,
                refreshToken,
                user.getNickname(),
                user.getRole().name()
        );
    }

    private KakaoUserInfoResponse getKakaoUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<KakaoUserInfoResponse> response = restTemplate.exchange(
                KAKAO_USER_INFO_URI,
                HttpMethod.GET,
                entity,
                KakaoUserInfoResponse.class
        );

        return response.getBody();
    }

    private User createUser(KakaoUserInfoResponse userInfo) {
        String nickname = "user";
        String email = "";

        if (userInfo.getKakaoAccount() != null) {
            email = userInfo.getKakaoAccount().getEmail();
            if (userInfo.getKakaoAccount().getProfile() != null) {
                nickname = userInfo.getKakaoAccount().getProfile().getNickname();
            }
        }

        User newUser = User.builder()
                .kakaoId(userInfo.getId())
                .email(email)
                .nickname(nickname)
                .role(Role.ROLE_USER) // 기본 권한은 USER로 설정
                .build();

        // 관리자로 가입시키고 싶다면 email이나 ID 등 특정 조건을 걸어 ROLE_ADMIN 부여 가능

        return userRepository.save(newUser);
    }

    private void syncUserProfile(User user, KakaoUserInfoResponse userInfo) {
        if (userInfo.getKakaoAccount() != null) {
            String email = userInfo.getKakaoAccount().getEmail();
            String nickname = null;
            if (userInfo.getKakaoAccount().getProfile() != null) {
                nickname = userInfo.getKakaoAccount().getProfile().getNickname();
            }
            user.updateProfile(nickname, email);
        }
    }
}
