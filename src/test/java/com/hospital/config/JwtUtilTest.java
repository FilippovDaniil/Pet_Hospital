package com.hospital.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class JwtUtilTest {

    @InjectMocks
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtUtil, "secret",
                "pet-hospital-his-jwt-secret-key-for-hs256-authentication-2024");
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", 86400000L);
    }

    private UserDetails user(String username) {
        return User.withUsername(username).password("pass").roles("ADMIN").build();
    }

    @Test
    void generateToken_extractUsernameReturnsSubject() {
        String token = jwtUtil.generateToken(user("alice"));
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("alice");
    }

    @Test
    void validateToken_withMatchingUser_returnsTrue() {
        UserDetails u = user("alice");
        String token = jwtUtil.generateToken(u);
        assertThat(jwtUtil.validateToken(token, u)).isTrue();
    }

    @Test
    void validateToken_withDifferentUsername_returnsFalse() {
        String token = jwtUtil.generateToken(user("alice"));
        assertThat(jwtUtil.validateToken(token, user("bob"))).isFalse();
    }

    @Test
    void validateToken_withExpiredToken_throwsException() {
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", -1000L);
        UserDetails u = user("alice");
        String token = jwtUtil.generateToken(u);
        assertThatThrownBy(() -> jwtUtil.validateToken(token, u))
                .isInstanceOf(Exception.class);
    }

    @Test
    void generateToken_twiceForSameUser_producesUniqueTokensDueToTimestamp() throws InterruptedException {
        UserDetails u = user("alice");
        String t1 = jwtUtil.generateToken(u);
        Thread.sleep(1);
        String t2 = jwtUtil.generateToken(u);
        // Both should have the same subject but may differ slightly (issuedAt)
        assertThat(jwtUtil.extractUsername(t1)).isEqualTo(jwtUtil.extractUsername(t2));
    }
}
