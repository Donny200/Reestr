package adliya.uz.task1.controller;

import adliya.uz.task1.config.MfaProperties;
import adliya.uz.task1.config.security.SessionCookies;
import adliya.uz.task1.dto.MfaCodeRequest;
import adliya.uz.task1.dto.MfaConfirmResponse;
import adliya.uz.task1.dto.MfaDisableRequest;
import adliya.uz.task1.dto.MfaEnrollmentResponse;
import adliya.uz.task1.dto.MfaStatusResponse;
import adliya.uz.task1.dto.UserResponse;
import adliya.uz.task1.entity.MfaChallenge;
import adliya.uz.task1.entity.User;
import adliya.uz.task1.exception.MfaRequiredException;
import adliya.uz.task1.service.AuthService;
import adliya.uz.task1.service.MfaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Second-factor endpoints. Enrollment and verification work either for a signed-in user or for the holder of the
 * single-use challenge cookie that the password step issued; that cookie never reaches any other endpoint.
 */
@RestController
@RequestMapping("/api/auth/mfa")
@RequiredArgsConstructor
public class MfaController {

    private final MfaService mfaService;
    private final AuthService authService;
    private final SessionCookies sessionCookies;
    private final MfaProperties properties;

    @GetMapping
    public MfaStatusResponse status() {
        return mfaService.status(authService.getCurrentUserEntity());
    }

    /** Completes the sign-in of an enrolled user; only now are the access and refresh tokens issued. */
    @PostMapping("/verify")
    public ResponseEntity<UserResponse> verify(@Valid @RequestBody MfaCodeRequest request, HttpServletRequest http) {
        String challenge = requireChallenge(http);
        User user = mfaService.verify(challenge, request.code(), clientAddress(http));
        return sessionCookies.start(user, http)
                .header(HttpHeaders.SET_COOKIE, sessionCookies.clearChallengeCookie().toString())
                .body(withMfa(user));
    }

    @PostMapping("/enroll")
    public MfaEnrollmentResponse enroll(HttpServletRequest http) {
        return mfaService.startEnrollment(actor(http));
    }

    /** Confirms enrollment with a first valid code. For a pending sign-in this also starts the session. */
    @PostMapping("/confirm")
    public ResponseEntity<MfaConfirmResponse> confirm(@Valid @RequestBody MfaCodeRequest request, HttpServletRequest http) {
        User signedIn = currentUser();
        if (signedIn != null) {
            List<String> codes = mfaService.confirmEnrollment(signedIn, request.code(), clientAddress(http));
            return ResponseEntity.ok(new MfaConfirmResponse(codes, null));
        }
        String challenge = requireChallenge(http);
        User user = mfaService.userOfChallenge(challenge, MfaChallenge.Purpose.ENROLL);
        List<String> codes = mfaService.confirmEnrollment(user, request.code(), clientAddress(http));
        mfaService.consumeChallenge(challenge);
        return sessionCookies.start(user, http)
                .header(HttpHeaders.SET_COOKIE, sessionCookies.clearChallengeCookie().toString())
                .body(new MfaConfirmResponse(codes, withMfa(user)));
    }

    @PostMapping("/disable")
    public MfaStatusResponse disable(@Valid @RequestBody MfaDisableRequest request, HttpServletRequest http) {
        User user = authService.getCurrentUserEntity();
        mfaService.disable(user, request.password(), request.code(), clientAddress(http));
        return mfaService.status(user);
    }

    @PostMapping("/recovery-codes")
    public MfaConfirmResponse regenerateRecoveryCodes(@Valid @RequestBody MfaDisableRequest request, HttpServletRequest http) {
        User user = authService.getCurrentUserEntity();
        return new MfaConfirmResponse(mfaService.regenerateRecoveryCodes(user, request.password(), request.code(), clientAddress(http)), null);
    }

    private User actor(HttpServletRequest http) {
        User signedIn = currentUser();
        if (signedIn != null) {
            return signedIn;
        }
        return mfaService.userOfChallenge(requireChallenge(http), MfaChallenge.Purpose.ENROLL);
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof UserDetails)) {
            return null;
        }
        return authService.getCurrentUserEntity();
    }

    private String requireChallenge(HttpServletRequest http) {
        String challenge = sessionCookies.challengeToken(http);
        if (!StringUtils.hasText(challenge)) {
            throw new MfaRequiredException("Sign in again");
        }
        return challenge;
    }

    private String clientAddress(HttpServletRequest request) {
        return ClientAddresses.of(request, properties.getClientAddressHeader());
    }

    private UserResponse withMfa(User user) {
        return UserResponse.from(user).withMfa(mfaService.summary(user));
    }
}
