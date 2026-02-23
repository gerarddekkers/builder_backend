-- Builder Projects: server-side project storage (replaces localStorage)
-- Run this on the Metro MySQL database (test + production)

CREATE TABLE IF NOT EXISTS builder_projects (
    id           VARCHAR(36)  NOT NULL PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    project_data LONGTEXT     NOT NULL,
    current_step TINYINT      NOT NULL DEFAULT 1,
    created_by   VARCHAR(100) NOT NULL,
    updated_by   VARCHAR(100) NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_builder_projects_updated (updated_at DESC),
    INDEX idx_builder_projects_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
