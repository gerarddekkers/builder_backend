package com.mentesme.builder.api;

import com.mentesme.builder.entity.BuilderUser;
import com.mentesme.builder.entity.BuilderUser.Role;
import com.mentesme.builder.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@CrossOrigin(origins = {"http://localhost:5173", "https://builder.mentes.me", "https://builder-prod.mentes.me"})
public class UserAdminController {

    private final UserService userService;

    public UserAdminController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> listUsers(HttpServletRequest request) {
        requireAdmin(request);
        List<UserResponse> users = userService.listUsers().stream()
                .map(UserResponse::from)
                .toList();
        return ResponseEntity.ok(users);
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@RequestBody CreateUserRequest body, HttpServletRequest request) {
        requireAdmin(request);
        if (body.username == null || body.username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gebruikersnaam is verplicht");
        }
        if (body.password == null || body.password.length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Wachtwoord moet minimaal 6 tekens zijn");
        }
        Role role = parseRole(body.role);
        try {
            BuilderUser user = userService.createUser(body.username, body.displayName, body.password, role);
            return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(@PathVariable Long id, @RequestBody UpdateUserRequest body, HttpServletRequest request) {
        requireAdmin(request);
        Role role = body.role != null ? parseRole(body.role) : null;
        try {
            BuilderUser user = userService.updateUser(id, body.displayName, role, body.active);
            return ResponseEntity.ok(UserResponse.from(user));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<Map<String, String>> changePassword(@PathVariable Long id, @RequestBody ChangePasswordRequest body, HttpServletRequest request) {
        requireAdmin(request);
        if (body.password == null || body.password.length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Wachtwoord moet minimaal 6 tekens zijn");
        }
        try {
            userService.changePassword(id, body.password);
            return ResponseEntity.ok(Map.of("status", "password_changed"));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deactivateUser(@PathVariable Long id, HttpServletRequest request) {
        requireAdmin(request);
        try {
            userService.deactivateUser(id);
            return ResponseEntity.ok(Map.of("status", "deactivated"));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    private void requireAdmin(HttpServletRequest request) {
        String role = (String) request.getAttribute("userRole");
        if (!"ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN role vereist");
        }
    }

    private Role parseRole(String role) {
        try {
            return Role.valueOf(role.toUpperCase());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ongeldige rol: " + role + ". Gebruik ADMIN of BUILDER.");
        }
    }

    // ── DTOs ──────────────────────────────────────────────────────────

    public record CreateUserRequest(String username, String displayName, String password, String role) {}
    public record UpdateUserRequest(String displayName, String role, Boolean active) {}
    public record ChangePasswordRequest(String password) {}

    public record UserResponse(Long id, String username, String displayName, String role, boolean active, String createdAt) {
        static UserResponse from(BuilderUser user) {
            return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole().name(),
                user.isActive(),
                user.getCreatedAt() != null ? user.getCreatedAt().toString() : null
            );
        }
    }
}
