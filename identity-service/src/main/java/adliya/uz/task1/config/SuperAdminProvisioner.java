package adliya.uz.task1.config;

import adliya.uz.task1.config.security.SystemRole;
import adliya.uz.task1.entity.Role;
import adliya.uz.task1.entity.User;
import adliya.uz.task1.repository.RoleRepository;
import adliya.uz.task1.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class SuperAdminProvisioner {

    private static final int MIN_PASSWORD_LENGTH = 16;
    private static final int MAX_PASSWORD_LENGTH = 200;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern BCRYPT_PATTERN = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$.{53}$");

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public boolean superAdminExists() {
        return userRepository.existsByRole_Name(SystemRole.SUPER_ADMIN.authority());
    }

    @Transactional
    public Optional<String> provisionIfMissing(String rawEmail, String rawPassword) {
        if (superAdminExists()) {
            return Optional.empty();
        }

        String email = normalizeAndValidateEmail(rawEmail);
        validatePassword(rawPassword);

        Role role = roleRepository.findByNameForUpdate(SystemRole.SUPER_ADMIN.authority())
                .orElseThrow(() -> new IllegalStateException("ROLE_SUPER_ADMIN is not initialized"));
        if (superAdminExists()) {
            return Optional.empty();
        }

        if (userRepository.existsByEmail(email)) {
            throw new IllegalStateException("Bootstrap email is already assigned to a non-SUPER_ADMIN user");
        }

        String encodedPassword = passwordEncoder.encode(rawPassword);
        if (encodedPassword == null || !BCRYPT_PATTERN.matcher(encodedPassword).matches()) {
            throw new IllegalStateException("Bootstrap password encoder must produce a BCrypt hash");
        }

        User superAdmin = User.builder()
                .email(email)
                .password(encodedPassword)
                .role(role)
                .firstName("Bootstrap")
                .lastName("Administrator")
                .enabled(true)
                .mustChangePassword(true)
                .build();
        userRepository.save(superAdmin);
        return Optional.of(email);
    }

    private String normalizeAndValidateEmail(String rawEmail) {
        if (rawEmail == null) {
            throw new IllegalStateException("Bootstrap SUPER_ADMIN email is required");
        }

        String email = rawEmail.strip().toLowerCase(Locale.ROOT);
        if (email.length() > 120 || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalStateException("Bootstrap SUPER_ADMIN email is invalid");
        }
        return email;
    }

    private void validatePassword(String rawPassword) {
        if (rawPassword == null
                || rawPassword.isBlank()
                || rawPassword.length() < MIN_PASSWORD_LENGTH
                || rawPassword.length() > MAX_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "Bootstrap SUPER_ADMIN password must contain between 16 and 200 characters"
            );
        }
    }
}
