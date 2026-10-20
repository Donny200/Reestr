package adliya.uz.task1.controller;

import adliya.uz.task1.dto.CreateUserRequest;
import adliya.uz.task1.dto.PasswordResetLinkResponse;
import adliya.uz.task1.dto.UpdateUserRequest;
import adliya.uz.task1.dto.UserResponse;
import adliya.uz.task1.dto.MfaStatusResponse;
import adliya.uz.task1.dto.SessionResponse;
import adliya.uz.task1.dto.SecurityEventResponse;
import adliya.uz.task1.service.MfaService;
import adliya.uz.task1.service.PasswordResetService;
import adliya.uz.task1.service.SessionService;
import adliya.uz.task1.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final PasswordResetService passwordResetService;
    private final MfaService mfaService;
    private final SessionService sessionService;

    @PostMapping
    @PreAuthorize("hasAuthority('USERS_CREATE')")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request));
    }

    /**
     * Without {@code page}/{@code size} the full list is returned (existing contract). With them, one page ordered by
     * id is returned and {@code X-Total-Count} carries the overall number of users.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('USERS_VIEW')")
    public ResponseEntity<List<UserResponse>> getAllUsers(@RequestParam(required = false) Integer page,
                                                          @RequestParam(required = false) Integer size) {
        if (page == null && size == null) {
            return ResponseEntity.ok(userService.withMfaStatus(userService.getAllForLegacyApi(), mfaService));
        }
        var users = userService.getPageForLegacyApi(Math.max(0, page == null ? 0 : page),
                Math.min(Math.max(1, size == null ? 50 : size), 200));
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(users.getTotalElements()))
                .body(userService.withMfaStatus(users.getContent(), mfaService));
    }

    /** SUPER_ADMIN only: enrollment state (never the secret or codes). */
    @GetMapping("/{id}/mfa")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public MfaStatusResponse mfaStatus(@PathVariable Long id) {
        return mfaService.status(userService.getById(id));
    }

    /** SUPER_ADMIN only: removes the user's second factor and ends their sessions; they re-enroll at the next sign-in. */
    @PostMapping("/{id}/mfa/reset")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<Void> resetMfa(@PathVariable Long id) {
        mfaService.adminReset(userService.getCurrentUser(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/sessions")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public List<SessionResponse> sessions(@PathVariable Long id) {
        return sessionService.sessionsForAdmin(id);
    }

    @DeleteMapping("/{id}/sessions/{sessionId}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<Void> revokeSession(@PathVariable Long id, @PathVariable String sessionId) {
        sessionService.revokeForAdmin(id, sessionId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/sessions")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<Void> revokeAllSessions(@PathVariable Long id) {
        sessionService.revokeAllForAdmin(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/security-events")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public List<SecurityEventResponse> securityEvents(@PathVariable Long id) {
        return sessionService.securityEvents(id).stream().map(SecurityEventResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USERS_VIEW')")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getForLegacyApi(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USERS_EDIT')")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        return ResponseEntity.ok(userService.update(id, request));
    }

    /**
     * SUPER_ADMIN only: issues a single-use reset link for the user. The raw token is returned exactly once and
     * never stored or logged; the user chooses the new password through the link. Passwords are never displayed.
     */
    @PostMapping("/{id}/password-reset")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<PasswordResetLinkResponse> initiatePasswordReset(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(passwordResetService.initiateByAdmin(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USERS_DEACTIVATE')")
    public ResponseEntity<Void> deactivateUser(@PathVariable Long id) {
        userService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
