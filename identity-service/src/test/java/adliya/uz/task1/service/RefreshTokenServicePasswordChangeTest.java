package adliya.uz.task1.service;

import adliya.uz.task1.config.security.JwtProperties;
import adliya.uz.task1.entity.RefreshToken;
import adliya.uz.task1.entity.User;
import adliya.uz.task1.exception.InvalidRefreshTokenException;
import adliya.uz.task1.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServicePasswordChangeTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private JwtProperties jwtProperties;
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setRefreshExpiration(60_000L);
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, jwtProperties);
    }

    @Test
    void refreshTokenIsNotCreatedBeforeMandatoryPasswordChange() {
        User user = User.builder().mustChangePassword(true).build();

        assertThatThrownBy(() -> refreshTokenService.createRefreshToken(user))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessageContaining("Password change");

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void rotateRevokesAllSessionsAndRejectsFlaggedUserWithoutAccessToken() {
        User user = User.builder().mustChangePassword(true).build();
        RefreshToken stored = RefreshToken.builder()
                .user(user)
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> refreshTokenService.rotate("raw-refresh-token"))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessageContaining("Password change");

        verify(refreshTokenRepository).revokeAllByUser(user);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void ordinaryUserCanStillRotateRefreshToken() {
        User user = User.builder().mustChangePassword(false).build();
        RefreshToken stored = RefreshToken.builder()
                .user(user)
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        RefreshTokenService.RotationResult result = refreshTokenService.rotate("raw-refresh-token");

        assertThat(stored.isRevoked()).isTrue();
        assertThat(result.user()).isSameAs(user);
        assertThat(result.rawToken()).isNotBlank();
        verify(refreshTokenRepository).save(stored);
        verify(refreshTokenRepository).save(argThat(token -> token != stored
                && token.getUser() == user
                && !token.isRevoked()));
    }
}
