package com.mentesme.builder.service;

import com.mentesme.builder.config.AuthProperties;
import com.mentesme.builder.entity.BuilderUser;
import com.mentesme.builder.entity.BuilderUser.Role;
import com.mentesme.builder.repository.BuilderUserRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final String DEFAULT_ADMIN_USERNAME = "support@mentes.me";
    private static final String DEFAULT_ADMIN_DISPLAY = "MentesMe Support";

    private final BuilderUserRepository userRepository;
    private final AuthProperties authProperties;
    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(BuilderUserRepository userRepository,
                       AuthProperties authProperties,
                       JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.authProperties = authProperties;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        ensureTableExists();
        seedAdminUser();
    }

    private void ensureTableExists() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS builder_users (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    username VARCHAR(100) UNIQUE NOT NULL,
                    display_name VARCHAR(255),
                    password_hash VARCHAR(255) NOT NULL,
                    role ENUM('ADMIN', 'BUILDER') NOT NULL DEFAULT 'BUILDER',
                    active TINYINT(1) NOT NULL DEFAULT 1,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_builder_users_username (username),
                    INDEX idx_builder_users_active (active)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
            log.info("builder_users table ensured");
        } catch (Exception e) {
            log.warn("Could not create builder_users table: {}", e.getMessage());
        }
    }

    private void seedAdminUser() {
        try {
            if (userRepository.count() == 0) {
                String password = authProperties.getPassword();
                if (password == null || password.isBlank()) {
                    log.warn("No BUILDER_AUTH_PASSWORD set — cannot seed admin user");
                    return;
                }
                BuilderUser admin = new BuilderUser(
                    DEFAULT_ADMIN_USERNAME,
                    DEFAULT_ADMIN_DISPLAY,
                    passwordEncoder.encode(password),
                    Role.ADMIN
                );
                userRepository.save(admin);
                log.info("Seeded admin user: {}", DEFAULT_ADMIN_USERNAME);
            }
        } catch (Exception e) {
            log.warn("Could not seed admin user: {}", e.getMessage());
        }
    }

    // ── Authentication ────────────────────────────────────────────────

    public Optional<BuilderUser> authenticate(String username, String password) {
        Optional<BuilderUser> user = userRepository.findByUsernameAndActiveTrue(username);
        if (user.isPresent() && passwordEncoder.matches(password, user.get().getPasswordHash())) {
            return user;
        }
        return Optional.empty();
    }

    // ── CRUD ──────────────────────────────────────────────────────────

    public List<BuilderUser> listUsers() {
        return userRepository.findAllByOrderByUsernameAsc();
    }

    public Optional<BuilderUser> findById(Long id) {
        return userRepository.findById(id);
    }

    public BuilderUser createUser(String username, String displayName, String password, Role role) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Gebruikersnaam bestaat al: " + username);
        }
        BuilderUser user = new BuilderUser(
            username,
            displayName,
            passwordEncoder.encode(password),
            role
        );
        return userRepository.save(user);
    }

    public BuilderUser updateUser(Long id, String displayName, Role role, Boolean active) {
        BuilderUser user = userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Gebruiker niet gevonden: " + id));
        if (displayName != null) user.setDisplayName(displayName);
        if (role != null) user.setRole(role);
        if (active != null) user.setActive(active);
        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    public void changePassword(Long id, String newPassword) {
        BuilderUser user = userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Gebruiker niet gevonden: " + id));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    public void deactivateUser(Long id) {
        updateUser(id, null, null, false);
    }

    public boolean hasUsers() {
        return userRepository.countByActiveTrue() > 0;
    }
}
