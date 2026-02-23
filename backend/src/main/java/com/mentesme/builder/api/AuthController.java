package com.mentesme.builder.api;

import com.mentesme.builder.config.AuthProperties;
import com.mentesme.builder.service.TokenService;
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

    public AuthController(AuthProperties authProperties, TokenService tokenService) {
        this.authProperties = authProperties;
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        // If auth is disabled, return a dummy token with ADMIN role
        if (!authProperties.isEnabled()) {
            return ResponseEntity.ok(new LoginResponse("auth-disabled", "ADMIN"));
        }

        // Validate credentials — all authenticated users get ADMIN role
        if (authProperties.getUsername().equals(request.username()) &&
            authProperties.getPassword().equals(request.password())) {
            String role = "ADMIN";
            String token = tokenService.generateToken(request.username(), role);
            return ResponseEntity.ok(new LoginResponse(token, role));
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
    public record LoginResponse(String token, String role) {}
}
