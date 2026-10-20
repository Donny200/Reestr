package adliya.uz.task1.service;

import adliya.uz.task1.dto.ChangePasswordRequest;
import adliya.uz.task1.dto.LoginRequest;
import adliya.uz.task1.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;

    public User login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        return userService.getByEmail(request.getEmail());
    }

    @Transactional(readOnly = true)
    public User getCurrentUserEntity() {
        User user = userService.getCurrentUser();
        user.getRole().getPermissions().size();
        user.getOrganizations().size();
        return user;
    }

    public User changePassword(ChangePasswordRequest request) {
        User user = userService.getCurrentUser();
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadCredentialsException("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        user.setCredentialsUpdatedAt(Instant.now());
        return userService.save(user);
    }
}
