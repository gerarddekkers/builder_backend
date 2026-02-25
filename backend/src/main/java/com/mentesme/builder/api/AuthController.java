package com.mentesme.builder.api;

import com.mentesme.builder.config.AuthProperties;
import com.mentesme.builder.entity.BuilderUser;
import com.mentesme.builder.service.TokenService;
import com.mentesme.builder.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = {"http://localhost:5173", "https://builder.mentes.me", "https://builder-prod.mentes.me"})
public class AuthController {

    private final AuthProperties authProperties;
    private final TokenService tokenService;
    private final UserService userService;

    public AuthController(AuthProperties authProperties, TokenService tokenService, UserService userService) {
        this.authProperties = authProperties;
        this.tokenService = tokenService;
        this.userService = userService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        // If auth is disabled, return a dummy token with ADMIN role + full access
        if (!authProperties.isEnabled()) {
            return ResponseEntity.ok(new LoginResponse("auth-disabled", "ADMIN", "Developer", 0L,
                new AccessFlags(true, true, true, true)));
        }

        // Try database login first
        var dbUser = userService.authenticate(request.username(), request.password());
        if (dbUser.isPresent()) {
            BuilderUser user = dbUser.get();
            String role = user.getRole().name();
            String token = tokenService.generateToken(user.getId(), user.getUsername(), role);
            String displayName = user.getDisplayName() != null ? user.getDisplayName() : user.getUsername();
            var access = new AccessFlags(
                user.isAccessAssessmentTest(), user.isAccessAssessmentProd(),
                user.isAccessJourneysTest(), user.isAccessJourneysProd()
            );
            return ResponseEntity.ok(new LoginResponse(token, role, displayName, user.getId(), access));
        }

        // Fallback: legacy env-var login (migration safety)
        if (userService.hasUsers()) {
            // DB has users — don't fall back to env var
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Onjuiste inloggegevens"));
        }

        // No DB users exist — use legacy env-var auth
        if (authProperties.getUsername().equals(request.username()) &&
            authProperties.getPassword().equals(request.password())) {
            String token = tokenService.generateToken(request.username(), "ADMIN");
            return ResponseEntity.ok(new LoginResponse(token, "ADMIN", request.username(), 0L,
                new AccessFlags(true, true, true, true)));
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Onjuiste inloggegevens"));
    }

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok(Map.of(
                "authEnabled", authProperties.isEnabled()
        ));
    }

    public record LoginRequest(String username, String password) {}
    public record AccessFlags(boolean assessmentTest, boolean assessmentProd,
                              boolean journeysTest, boolean journeysProd) {}
    public record LoginResponse(String token, String role, String displayName, Long userId,
                                AccessFlags access) {}
}
