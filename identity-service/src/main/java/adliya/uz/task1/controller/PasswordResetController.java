package adliya.uz.task1.controller;

import adliya.uz.task1.config.PasswordResetProperties;
import adliya.uz.task1.dto.CompletePasswordResetRequest;
import adliya.uz.task1.dto.PasswordResetOptionsResponse;
import adliya.uz.task1.dto.PasswordResetRequest;
import adliya.uz.task1.service.PasswordResetRateLimiter;
import adliya.uz.task1.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Anonymous password-reset endpoints: rate limited, enumeration-safe, never returning tokens or passwords. */
@RestController
@RequestMapping("/api/auth/password-reset")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;
    private final PasswordResetRateLimiter rateLimiter;
    private final PasswordResetProperties properties;

    @GetMapping("/options")
    public PasswordResetOptionsResponse options() {
        return new PasswordResetOptionsResponse(passwordResetService.selfServiceAvailable());
    }

    @PostMapping("/request")
    public ResponseEntity<Map<String, Boolean>> request(@Valid @RequestBody PasswordResetRequest request, HttpServletRequest http) {
        rateLimiter.acquire(clientAddress(http));
        passwordResetService.requestByEmail(request.getEmail());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("accepted", true));
    }

    @PostMapping("/complete")
    public ResponseEntity<Map<String, Boolean>> complete(@Valid @RequestBody CompletePasswordResetRequest request, HttpServletRequest http) {
        rateLimiter.acquire(clientAddress(http));
        passwordResetService.complete(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("reset", true));
    }

    private String clientAddress(HttpServletRequest request) {
        return ClientAddresses.of(request, properties.getClientAddressHeader());
    }
}
