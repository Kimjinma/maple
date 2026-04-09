package com.example.maple.security;

import com.example.maple.domain.User;
import com.example.maple.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // username 파라미터를 DB PK 값(id)으로 취급.
        Long userId = Long.valueOf(username);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("해당 유저(id=" + username + ")를 찾을 수 없습니다."));

        return new CustomUserDetails(user);
    }
}
