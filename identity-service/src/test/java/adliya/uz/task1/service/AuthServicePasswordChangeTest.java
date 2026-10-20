package adliya.uz.task1.service;

import adliya.uz.task1.dto.ChangePasswordRequest;
import adliya.uz.task1.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServicePasswordChangeTest {

    @Mock
    private UserService userService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void successfulPasswordChangeClearsMandatoryChangeFlag() {
        User user = User.builder()
                .password("old-hash")
                .mustChangePassword(true)
                .build();
        ChangePasswordRequest request = request("current-password", "new-password-2026");
        when(userService.getCurrentUser()).thenReturn(user);
        when(passwordEncoder.matches("current-password", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("new-password-2026")).thenReturn("new-hash");
        when(userService.save(user)).thenReturn(user);
        AuthService authService = new AuthService(userService, authenticationManager, passwordEncoder);

        User result = authService.changePassword(request);

        assertThat(result).isSameAs(user);
        assertThat(user.getPassword()).isEqualTo("new-hash");
        assertThat(user.getMustChangePassword()).isFalse();
        verify(userService).save(user);
    }

    @Test
    void incorrectCurrentPasswordKeepsMandatoryChangeFlag() {
        User user = User.builder()
                .password("old-hash")
                .mustChangePassword(true)
                .build();
        ChangePasswordRequest request = request("wrong-password", "new-password-2026");
        when(userService.getCurrentUser()).thenReturn(user);
        when(passwordEncoder.matches("wrong-password", "old-hash")).thenReturn(false);
        AuthService authService = new AuthService(userService, authenticationManager, passwordEncoder);

        assertThatThrownBy(() -> authService.changePassword(request))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(user.getMustChangePassword()).isTrue();
        verify(userService, never()).save(user);
    }

    private ChangePasswordRequest request(String currentPassword, String newPassword) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword(currentPassword);
        request.setNewPassword(newPassword);
        return request;
    }
}
