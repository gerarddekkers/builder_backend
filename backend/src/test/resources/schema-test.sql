-- Schema for integration tests — H2 MySQL mode compatible.
-- Matches metro-prod.sql structure (sans MySQL-specific syntax).

CREATE TABLE IF NOT EXISTS `groups` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `learning_journeys` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) DEFAULT NULL,
  `nameEn` varchar(50) DEFAULT NULL,
  `ljKey` varchar(20) DEFAULT NULL,
  `description` varchar(50) DEFAULT NULL,
  `descriptionEn` varchar(50) DEFAULT NULL,
  `aiCoachEnabled` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `labels` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `identifier` varchar(100) DEFAULT NULL,
  `text` varchar(10000) DEFAULT NULL,
  `lang` varchar(5) DEFAULT NULL,
  `category` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `steps` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `position` int DEFAULT NULL,
  `title` varchar(100) DEFAULT NULL,
  `learningJourneyId` bigint DEFAULT NULL,
  `colour` varchar(20) DEFAULT NULL,
  `size` varchar(20) DEFAULT NULL,
  `textContent` varchar(100) DEFAULT NULL,
  `role` varchar(20) DEFAULT NULL,
  `documents` varchar(50) DEFAULT NULL,
  `conversation` varchar(20) DEFAULT NULL,
  `type` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `steps_ibfk_1` FOREIGN KEY (`learningJourneyId`) REFERENCES `learning_journeys` (`id`)
);

CREATE TABLE IF NOT EXISTS `step_question` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `stepId` bigint DEFAULT NULL,
  `question` varchar(100) DEFAULT NULL,
  `order` int DEFAULT NULL,
  `type` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `step_question_ibfk_1` FOREIGN KEY (`stepId`) REFERENCES `steps` (`id`)
);

CREATE TABLE IF NOT EXISTS `learning_journey_documents` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `identifier` varchar(50) DEFAULT NULL,
  `label` varchar(100) DEFAULT NULL,
  `url` varchar(500) DEFAULT NULL,
  `lang` varchar(5) DEFAULT NULL,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `group_learning_journey` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `groupId` bigint DEFAULT NULL,
  `learningJourneyId` bigint DEFAULT NULL,
  `assignedAt` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  CONSTRAINT `glj_ibfk_1` FOREIGN KEY (`learningJourneyId`) REFERENCES `learning_journeys` (`id`),
  CONSTRAINT `glj_ibfk_2` FOREIGN KEY (`groupId`) REFERENCES `groups` (`id`)
);

-- Seed test groups (needed for FK constraint)
MERGE INTO `groups` (`id`, `name`) KEY(`id`) VALUES (1, 'Test Group');
MERGE INTO `groups` (`id`, `name`) KEY(`id`) VALUES (2, 'Second Group');
