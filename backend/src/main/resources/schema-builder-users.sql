-- builder_users: multi-user authenticatie voor Assessment Builder
-- Gebruikt door: UserService.java (seed + CRUD), AuthController.java (login)

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
