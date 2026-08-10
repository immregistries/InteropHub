-- MySQL dump 10.13  Distrib 8.1.0, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: interophub
-- ------------------------------------------------------
-- Server version	8.1.0

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `admin_note`
--

DROP TABLE IF EXISTS `admin_note`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin_note` (
  `note_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `scope_type` enum('APP','WORKSPACE','SYSTEM','USER','TOKEN') NOT NULL,
  `scope_id` bigint unsigned NOT NULL,
  `note_text` text NOT NULL,
  `created_by_user_id` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`note_id`),
  KEY `ix_admin_note_scope` (`scope_type`,`scope_id`,`created_at`),
  KEY `fk_admin_note_creator` (`created_by_user_id`),
  CONSTRAINT `fk_admin_note_creator` FOREIGN KEY (`created_by_user_id`) REFERENCES `auth_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `app_api`
--

DROP TABLE IF EXISTS `app_api`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `app_api` (
  `api_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `app_id` bigint unsigned NOT NULL,
  `api_code` varchar(80) NOT NULL,
  `purpose_label` varchar(160) NOT NULL,
  `description` text,
  `is_enabled` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`api_id`),
  UNIQUE KEY `uq_app_api_code` (`app_id`,`api_code`),
  KEY `ix_app_api_enabled` (`app_id`,`is_enabled`),
  CONSTRAINT `fk_app_api_app` FOREIGN KEY (`app_id`) REFERENCES `app_registry` (`app_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `app_api_secret`
--

DROP TABLE IF EXISTS `app_api_secret`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `app_api_secret` (
  `secret_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `api_id` bigint unsigned NOT NULL,
  `user_id` bigint NOT NULL,
  `secret_value` varchar(255) DEFAULT NULL,
  `label` varchar(120) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`secret_id`),
  UNIQUE KEY `uq_api_secret_user` (`api_id`,`user_id`),
  KEY `ix_api_secret_user` (`user_id`),
  CONSTRAINT `fk_api_secret_api` FOREIGN KEY (`api_id`) REFERENCES `app_api` (`api_id`),
  CONSTRAINT `fk_api_secret_user` FOREIGN KEY (`user_id`) REFERENCES `auth_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `app_login_event`
--

DROP TABLE IF EXISTS `app_login_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `app_login_event` (
  `event_id` bigint NOT NULL AUTO_INCREMENT,
  `app_id` bigint NOT NULL,
  `logged_in_at` datetime(6) NOT NULL,
  `login_code_id` bigint DEFAULT NULL,
  `server_ip` varchar(45) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `user_ip` varchar(45) DEFAULT NULL,
  PRIMARY KEY (`event_id`)
) ENGINE=InnoDB AUTO_INCREMENT=187 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `app_redirect_allowlist`
--

DROP TABLE IF EXISTS `app_redirect_allowlist`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `app_redirect_allowlist` (
  `allow_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `app_id` bigint unsigned NOT NULL,
  `base_url` varchar(255) NOT NULL,
  `is_enabled` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`allow_id`),
  UNIQUE KEY `uq_app_base` (`app_id`,`base_url`),
  KEY `ix_allow_enabled` (`app_id`,`is_enabled`),
  CONSTRAINT `fk_allow_app` FOREIGN KEY (`app_id`) REFERENCES `app_registry` (`app_id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `app_registry`
--

DROP TABLE IF EXISTS `app_registry`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `app_registry` (
  `app_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `app_code` varchar(60) NOT NULL,
  `app_name` varchar(120) NOT NULL,
  `default_redirect_url` varchar(255) DEFAULT NULL,
  `app_description` text,
  `managed_by` enum('AIRA','THIRD_PARTY') NOT NULL DEFAULT 'AIRA',
  `is_enabled` tinyint(1) NOT NULL DEFAULT '1',
  `kill_switch` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `is_visible` bit(1) NOT NULL DEFAULT b'1',
  PRIMARY KEY (`app_id`),
  UNIQUE KEY `uq_app_code` (`app_code`),
  KEY `ix_app_enabled` (`is_enabled`,`kill_switch`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `app_user_token`
--

DROP TABLE IF EXISTS `app_user_token`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `app_user_token` (
  `token_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `app_id` bigint unsigned NOT NULL,
  `token_hash` varbinary(32) NOT NULL,
  `label` varchar(120) DEFAULT NULL,
  `issued_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `expires_at` datetime NOT NULL,
  `revoked_at` datetime DEFAULT NULL,
  `last_used_at` datetime DEFAULT NULL,
  PRIMARY KEY (`token_id`),
  UNIQUE KEY `uq_app_token_hash` (`token_hash`),
  KEY `ix_app_user_active` (`app_id`,`user_id`,`revoked_at`,`expires_at`),
  KEY `ix_app_token_expires` (`expires_at`),
  KEY `fk_app_token_user` (`user_id`),
  CONSTRAINT `fk_app_token_app` FOREIGN KEY (`app_id`) REFERENCES `app_registry` (`app_id`),
  CONSTRAINT `fk_app_token_user` FOREIGN KEY (`user_id`) REFERENCES `auth_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `auth_login_code`
--

DROP TABLE IF EXISTS `auth_login_code`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_login_code` (
  `login_code_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `app_id` bigint unsigned NOT NULL,
  `code_hash` varbinary(32) NOT NULL,
  `return_to` varchar(500) DEFAULT NULL,
  `state_nonce` varchar(255) DEFAULT NULL,
  `requested_url` varchar(500) DEFAULT NULL,
  `issued_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `expires_at` datetime NOT NULL,
  `consumed_at` datetime DEFAULT NULL,
  PRIMARY KEY (`login_code_id`),
  UNIQUE KEY `uq_login_code_hash` (`code_hash`),
  KEY `ix_login_code_user` (`user_id`,`issued_at`),
  KEY `ix_login_code_expires` (`expires_at`),
  CONSTRAINT `fk_login_code_user` FOREIGN KEY (`user_id`) REFERENCES `auth_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=215 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `auth_magic_link`
--

DROP TABLE IF EXISTS `auth_magic_link`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_magic_link` (
  `magic_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `token_hash` varbinary(32) NOT NULL,
  `app_id` bigint unsigned DEFAULT NULL,
  `return_to` varchar(500) DEFAULT NULL,
  `state_nonce` varchar(255) DEFAULT NULL,
  `requested_url` varchar(500) DEFAULT NULL,
  `issued_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `expires_at` datetime NOT NULL,
  `consumed_at` datetime DEFAULT NULL,
  `request_ip` varbinary(16) DEFAULT NULL,
  `user_agent` varchar(300) DEFAULT NULL,
  PRIMARY KEY (`magic_id`),
  UNIQUE KEY `uq_magic_token_hash` (`token_hash`),
  KEY `ix_magic_user` (`user_id`,`issued_at`),
  KEY `ix_magic_expires` (`expires_at`),
  CONSTRAINT `fk_magic_user` FOREIGN KEY (`user_id`) REFERENCES `auth_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=192 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `auth_magic_link_send_event`
--

DROP TABLE IF EXISTS `auth_magic_link_send_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_magic_link_send_event` (
  `send_event_id` bigint NOT NULL AUTO_INCREMENT,
  `app_id` bigint DEFAULT NULL,
  `email_normalized` varchar(254) NOT NULL,
  `error_class` varchar(120) DEFAULT NULL,
  `error_message` varchar(1000) DEFAULT NULL,
  `event_at` datetime(6) NOT NULL,
  `event_type` enum('SEND_REQUESTED','SMTP_SEND_FAILED','SMTP_SEND_STARTED','SMTP_SEND_SUCCEEDED') NOT NULL,
  `magic_id` bigint DEFAULT NULL,
  `request_id` varchar(36) DEFAULT NULL,
  `request_ip` varbinary(16) DEFAULT NULL,
  `server_node` varchar(120) DEFAULT NULL,
  `smtp_message_id` varchar(255) DEFAULT NULL,
  `smtp_provider` varchar(80) DEFAULT NULL,
  `smtp_reply_code` varchar(32) DEFAULT NULL,
  `user_agent` varchar(300) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`send_event_id`)
) ENGINE=InnoDB AUTO_INCREMENT=427 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `auth_session`
--

DROP TABLE IF EXISTS `auth_session`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_session` (
  `session_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `session_token_hash` varbinary(32) NOT NULL,
  `issued_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `expires_at` datetime NOT NULL,
  `revoked_at` datetime DEFAULT NULL,
  `last_ip` varbinary(16) DEFAULT NULL,
  `last_user_agent` varchar(300) DEFAULT NULL,
  PRIMARY KEY (`session_id`),
  UNIQUE KEY `uq_session_token_hash` (`session_token_hash`),
  KEY `ix_session_user` (`user_id`,`expires_at`),
  KEY `ix_session_expires` (`expires_at`),
  CONSTRAINT `fk_session_user` FOREIGN KEY (`user_id`) REFERENCES `auth_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=158 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `auth_user`
--

DROP TABLE IF EXISTS `auth_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_user` (
  `user_id` bigint NOT NULL AUTO_INCREMENT,
  `email` varchar(254) NOT NULL,
  `email_normalized` varchar(254) NOT NULL,
  `display_name` varchar(160) DEFAULT NULL,
  `organization` varchar(200) DEFAULT NULL,
  `role_title` varchar(200) DEFAULT NULL,
  `email_verified` bit(1) NOT NULL DEFAULT b'0',
  `status` enum('ACTIVE','DELETED','DISABLED') NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `last_login_at` datetime(6) DEFAULT NULL,
  `last_seen_at` datetime(6) DEFAULT NULL,
  `delete_after_at` datetime(6) DEFAULT NULL,
  `first_name` varchar(100) DEFAULT NULL,
  `last_name` varchar(100) DEFAULT NULL,
  `timezone_id` varchar(64) DEFAULT NULL,
  `week_start_day` enum('SUNDAY','MONDAY') DEFAULT NULL,
  `is_admin` bit(1) NOT NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uq_auth_user_email_norm` (`email_normalized`),
  KEY `ix_auth_user_status` (`status`),
  KEY `ix_auth_user_delete_after` (`delete_after_at`)
) ENGINE=InnoDB AUTO_INCREMENT=86 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `connect_workspace`
--

DROP TABLE IF EXISTS `connect_workspace`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `connect_workspace` (
  `workspace_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `topic_id` bigint unsigned NOT NULL,
  `workspace_name` varchar(160) NOT NULL,
  `description` text,
  `start_date` date DEFAULT NULL,
  `end_date` date DEFAULT NULL,
  `status` enum('ACTIVE','CLOSED','ARCHIVED') NOT NULL DEFAULT 'ACTIVE',
  `requires_approval` tinyint(1) NOT NULL DEFAULT '1',
  `created_by_user_id` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`workspace_id`),
  KEY `ix_workspace_topic` (`topic_id`,`status`),
  KEY `fk_workspace_creator` (`created_by_user_id`),
  CONSTRAINT `fk_workspace_creator` FOREIGN KEY (`created_by_user_id`) REFERENCES `auth_user` (`user_id`),
  CONSTRAINT `fk_workspace_topic` FOREIGN KEY (`topic_id`) REFERENCES `ig_topic` (`topic_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `dandelion_sync_config`
--

DROP TABLE IF EXISTS `dandelion_sync_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dandelion_sync_config` (
  `config_id` bigint NOT NULL AUTO_INCREMENT,
  `es_topic_space_id` bigint unsigned NOT NULL,
  `active` bit(1) NOT NULL,
  `api_endpoint` varchar(500) NOT NULL,
  `api_key` varchar(300) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `sync_enabled` bit(1) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`config_id`),
  KEY `ix_dd_sync_config_space_active` (`es_topic_space_id`,`active`),
  CONSTRAINT `fk_dd_sync_config_space` FOREIGN KEY (`es_topic_space_id`) REFERENCES `es_topic_space` (`es_topic_space_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `dandelion_sync_queue`
--

DROP TABLE IF EXISTS `dandelion_sync_queue`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dandelion_sync_queue` (
  `sync_queue_id` bigint NOT NULL AUTO_INCREMENT,
  `es_topic_space_id` bigint unsigned NOT NULL,
  `attempt_count` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `entity_id` bigint NOT NULL,
  `entity_type` enum('ASSIGNMENT','CONTACT','TOPIC') NOT NULL,
  `last_error` text,
  `operation` enum('ASSIGN_ADD','ASSIGN_REMOVE','UPSERT') NOT NULL,
  `secondary_entity_id` bigint DEFAULT NULL,
  `sent_at` datetime(6) DEFAULT NULL,
  `status` enum('FAILED','PENDING','SENT') NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`sync_queue_id`),
  KEY `ix_dd_sync_queue_space_status` (`es_topic_space_id`,`status`,`entity_type`,`created_at`),
  CONSTRAINT `fk_dd_sync_queue_space` FOREIGN KEY (`es_topic_space_id`) REFERENCES `es_topic_space` (`es_topic_space_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `email_send_log`
--

DROP TABLE IF EXISTS `email_send_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `email_send_log` (
  `email_log_id` bigint NOT NULL AUTO_INCREMENT,
  `body_text` text,
  `email_reason` varchar(80) NOT NULL,
  `es_meeting_communication_id` bigint DEFAULT NULL,
  `magic_id` bigint DEFAULT NULL,
  `recipient_email` varchar(254) NOT NULL,
  `recipient_email_normalized` varchar(254) NOT NULL,
  `sent_at` datetime(6) NOT NULL,
  `smtp_message_id` varchar(255) DEFAULT NULL,
  `smtp_provider` varchar(80) DEFAULT NULL,
  `subject` varchar(500) NOT NULL,
  `user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`email_log_id`)
) ENGINE=InnoDB AUTO_INCREMENT=402 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_agenda_item_comment`
--

DROP TABLE IF EXISTS `es_agenda_item_comment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_agenda_item_comment` (
  `es_agenda_item_comment_id` bigint NOT NULL AUTO_INCREMENT,
  `comment_markdown` text NOT NULL,
  `comment_type` enum('CHANGE_REQUEST','COMMENT','DECLINE_REASON','MEETING_NOTE','POSTPONE_REQUEST') NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `email` varchar(254) DEFAULT NULL,
  `es_meeting_agenda_item_id` bigint NOT NULL,
  `user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`es_agenda_item_comment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_agenda_item_presenter`
--

DROP TABLE IF EXISTS `es_agenda_item_presenter`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_agenda_item_presenter` (
  `es_agenda_item_presenter_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `display_name` varchar(160) DEFAULT NULL,
  `email` varchar(254) NOT NULL,
  `email_normalized` varchar(254) NOT NULL,
  `es_meeting_agenda_item_id` bigint NOT NULL,
  `presenter_role` enum('FACILITATOR','LEAD','REQUESTED_REVIEWER','SUPPORTING') NOT NULL,
  `responded_at` datetime(6) DEFAULT NULL,
  `response_note` text,
  `status` enum('PROPOSED','INVITED','INVITE_BLOCKED','ACCEPTED','DECLINED','NEEDS_CHANGES','REMOVED') NOT NULL DEFAULT 'PROPOSED',
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`es_agenda_item_presenter_id`)
) ENGINE=InnoDB AUTO_INCREMENT=34 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_campaign`
--

DROP TABLE IF EXISTS `es_campaign`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_campaign` (
  `es_campaign_id` bigint NOT NULL AUTO_INCREMENT,
  `allow_general_comments` bit(1) NOT NULL,
  `allow_topic_comments` bit(1) NOT NULL,
  `campaign_code` varchar(80) NOT NULL,
  `campaign_name` varchar(160) NOT NULL,
  `campaign_type` varchar(80) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by_user_id` bigint NOT NULL,
  `current_round_no` int NOT NULL,
  `description` text,
  `end_at` datetime(6) DEFAULT NULL,
  `start_at` datetime(6) DEFAULT NULL,
  `status` enum('ACTIVE','ARCHIVED','CLOSED','DRAFT') NOT NULL,
  PRIMARY KEY (`es_campaign_id`),
  UNIQUE KEY `UK_1aj983v4b46p88tmp8in2q9s4` (`campaign_code`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_campaign_registration`
--

DROP TABLE IF EXISTS `es_campaign_registration`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_campaign_registration` (
  `es_campaign_registration_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `email` varchar(254) DEFAULT NULL,
  `email_normalized` varchar(254) DEFAULT NULL,
  `es_campaign_id` bigint NOT NULL,
  `first_name` varchar(100) NOT NULL,
  `general_updates_opt_in` bit(1) NOT NULL,
  `last_name` varchar(100) DEFAULT NULL,
  `session_key` varchar(128) DEFAULT NULL,
  PRIMARY KEY (`es_campaign_registration_id`),
  KEY `ix_es_reg_campaign_time` (`es_campaign_id`,`created_at`),
  KEY `ix_es_reg_campaign_email` (`es_campaign_id`,`email_normalized`),
  KEY `ix_es_reg_session_campaign` (`session_key`,`es_campaign_id`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_campaign_topic`
--

DROP TABLE IF EXISTS `es_campaign_topic`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_campaign_topic` (
  `es_campaign_topic_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `display_order` int NOT NULL,
  `es_campaign_id` bigint NOT NULL,
  `es_topic_id` bigint NOT NULL,
  `table_no` int DEFAULT NULL,
  `topic_set_no` int DEFAULT NULL,
  PRIMARY KEY (`es_campaign_topic_id`)
) ENGINE=InnoDB AUTO_INCREMENT=202 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_comment`
--

DROP TABLE IF EXISTS `es_comment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_comment` (
  `es_comment_id` bigint NOT NULL AUTO_INCREMENT,
  `comment_text` text NOT NULL,
  `comment_type` enum('GENERAL','NEW_TOPIC_SUGGESTION','TOPIC') NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `email` varchar(254) DEFAULT NULL,
  `email_normalized` varchar(254) DEFAULT NULL,
  `es_campaign_id` bigint NOT NULL,
  `es_topic_id` bigint DEFAULT NULL,
  `first_name` varchar(100) NOT NULL,
  `last_name` varchar(100) DEFAULT NULL,
  `session_key` varchar(128) DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`es_comment_id`)
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_interest`
--

DROP TABLE IF EXISTS `es_interest`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_interest` (
  `es_interest_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `es_campaign_id` bigint NOT NULL,
  `es_campaign_registration_id` bigint DEFAULT NULL,
  `es_topic_id` bigint NOT NULL,
  `round_no` int NOT NULL,
  `session_key` varchar(128) DEFAULT NULL,
  `table_no` int NOT NULL,
  PRIMARY KEY (`es_interest_id`)
) ENGINE=InnoDB AUTO_INCREMENT=332 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_live_vote`
--

DROP TABLE IF EXISTS `es_live_vote`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_live_vote` (
  `es_live_vote_id` bigint NOT NULL AUTO_INCREMENT,
  `es_recorded_outcome_id` bigint NOT NULL,
  `status` enum('CLOSED','OPEN','PREPARED') NOT NULL,
  `motion_text` text NOT NULL,
  `moved_by_user_id` bigint DEFAULT NULL,
  `moved_by_name` varchar(160) DEFAULT NULL,
  `seconded_by_user_id` bigint DEFAULT NULL,
  `seconded_by_name` varchar(160) DEFAULT NULL,
  `presiding_chair_user_id` bigint NOT NULL,
  `opened_at` datetime(6) DEFAULT NULL,
  `opened_by_user_id` bigint DEFAULT NULL,
  `closed_at` datetime(6) DEFAULT NULL,
  `closed_by_user_id` bigint DEFAULT NULL,
  `participant_count_observation_id` bigint DEFAULT NULL,
  `call_participant_count` int DEFAULT NULL,
  `expected_voter_count` int DEFAULT NULL,
  `electronic_for_count` int NOT NULL DEFAULT '0',
  `electronic_against_count` int NOT NULL DEFAULT '0',
  `electronic_abstain_count` int NOT NULL DEFAULT '0',
  `manual_for_count` int NOT NULL DEFAULT '0',
  `manual_against_count` int NOT NULL DEFAULT '0',
  `manual_abstain_count` int NOT NULL DEFAULT '0',
  `final_for_count` int DEFAULT NULL,
  `final_against_count` int DEFAULT NULL,
  `final_abstain_count` int DEFAULT NULL,
  `result` enum('APPROVED','FAILED','NO_RESULT','WITHDRAWN') DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by_user_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `updated_by_user_id` bigint NOT NULL,
  PRIMARY KEY (`es_live_vote_id`),
  UNIQUE KEY `uq_es_live_vote_outcome` (`es_recorded_outcome_id`),
  KEY `ix_es_live_vote_status_created` (`status`,`created_at`,`es_live_vote_id`),
  KEY `ix_es_live_vote_result_created` (`result`,`created_at`),
  KEY `fk_es_lv_moved_by` (`moved_by_user_id`),
  KEY `fk_es_lv_seconded_by` (`seconded_by_user_id`),
  KEY `fk_es_lv_presiding_chair` (`presiding_chair_user_id`),
  KEY `fk_es_lv_opened_by` (`opened_by_user_id`),
  KEY `fk_es_lv_closed_by` (`closed_by_user_id`),
  KEY `fk_es_lv_participant_count` (`participant_count_observation_id`),
  KEY `fk_es_lv_created_by` (`created_by_user_id`),
  KEY `fk_es_lv_updated_by` (`updated_by_user_id`),
  CONSTRAINT `fk_es_lv_closed_by` FOREIGN KEY (`closed_by_user_id`) REFERENCES `auth_user` (`user_id`),
  CONSTRAINT `fk_es_lv_created_by` FOREIGN KEY (`created_by_user_id`) REFERENCES `auth_user` (`user_id`),
  CONSTRAINT `fk_es_lv_moved_by` FOREIGN KEY (`moved_by_user_id`) REFERENCES `auth_user` (`user_id`),
  CONSTRAINT `fk_es_lv_opened_by` FOREIGN KEY (`opened_by_user_id`) REFERENCES `auth_user` (`user_id`),
  CONSTRAINT `fk_es_lv_outcome` FOREIGN KEY (`es_recorded_outcome_id`) REFERENCES `es_recorded_outcome` (`es_recorded_outcome_id`),
  CONSTRAINT `fk_es_lv_participant_count` FOREIGN KEY (`participant_count_observation_id`) REFERENCES `es_meeting_participant_count` (`es_meeting_participant_count_id`),
  CONSTRAINT `fk_es_lv_presiding_chair` FOREIGN KEY (`presiding_chair_user_id`) REFERENCES `auth_user` (`user_id`),
  CONSTRAINT `fk_es_lv_seconded_by` FOREIGN KEY (`seconded_by_user_id`) REFERENCES `auth_user` (`user_id`),
  CONSTRAINT `fk_es_lv_updated_by` FOREIGN KEY (`updated_by_user_id`) REFERENCES `auth_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_live_vote_response`
--

DROP TABLE IF EXISTS `es_live_vote_response`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_live_vote_response` (
  `es_live_vote_response_id` bigint NOT NULL AUTO_INCREMENT,
  `es_live_vote_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `response` enum('ABSTAIN','AGAINST','FOR') NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`es_live_vote_response_id`),
  UNIQUE KEY `uq_es_lvr_vote_user` (`es_live_vote_id`,`user_id`),
  KEY `ix_es_lvr_vote_response` (`es_live_vote_id`,`response`,`es_live_vote_response_id`),
  KEY `fk_es_lvr_user` (`user_id`),
  CONSTRAINT `fk_es_lvr_user` FOREIGN KEY (`user_id`) REFERENCES `auth_user` (`user_id`),
  CONSTRAINT `fk_es_lvr_vote` FOREIGN KEY (`es_live_vote_id`) REFERENCES `es_live_vote` (`es_live_vote_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_meeting`
--

DROP TABLE IF EXISTS `es_meeting`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_meeting` (
  `es_meeting_id` bigint NOT NULL AUTO_INCREMENT,
  `cancellation_reason` text,
  `cancelled_at` datetime(6) DEFAULT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by_user_id` bigint NOT NULL,
  `es_topic_meeting_id` bigint NOT NULL,
  `es_topic_space_id` bigint unsigned NOT NULL,
  `finalized_at` datetime(6) DEFAULT NULL,
  `meeting_description` text,
  `meeting_key` varchar(80) DEFAULT NULL,
  `meeting_name` varchar(160) NOT NULL,
  `scheduled_end` datetime(6) DEFAULT NULL,
  `scheduled_start` datetime(6) NOT NULL,
  `status` enum('CANCELLED','COMPLETED','CLOSED','DRAFT','FINALIZED','IN_SESSION','PROPOSED') NOT NULL,
  `designated_chair_user_id` bigint DEFAULT NULL,
  `designated_scribe_user_id` bigint DEFAULT NULL,
  `current_chair_user_id` bigint DEFAULT NULL,
  `current_scribe_user_id` bigint DEFAULT NULL,
  `current_agenda_item_id` bigint DEFAULT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  `started_by_user_id` bigint DEFAULT NULL,
  `completed_by_user_id` bigint DEFAULT NULL,
  `close_due_at` datetime(6) DEFAULT NULL,
  `closed_at` datetime(6) DEFAULT NULL,
  `closed_by_user_id` bigint DEFAULT NULL,
  `close_method` enum('AUTOMATIC','MANUAL') DEFAULT NULL,
  `timezone_id` varchar(64) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `online_meeting_details` text,
  `online_meeting_url` varchar(2048) DEFAULT NULL,
  PRIMARY KEY (`es_meeting_id`),
  KEY `ix_es_meeting_topic_space` (`es_topic_space_id`),
  KEY `ix_es_meeting_status_close_due` (`status`,`close_due_at`,`es_meeting_id`),
  KEY `ix_es_meeting_current_agenda` (`current_agenda_item_id`),
  CONSTRAINT `fk_es_meeting_topic_space` FOREIGN KEY (`es_topic_space_id`) REFERENCES `es_topic_space` (`es_topic_space_id`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_meeting_agenda_activity`
--

DROP TABLE IF EXISTS `es_meeting_agenda_activity`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_meeting_agenda_activity` (
  `es_meeting_agenda_activity_id` bigint NOT NULL AUTO_INCREMENT,
  `es_meeting_id` bigint NOT NULL,
  `es_meeting_agenda_item_id` bigint NOT NULL,
  `es_topic_id` bigint NOT NULL,
  `started_at` datetime(6) NOT NULL,
  `started_by_user_id` bigint NOT NULL,
  `ended_at` datetime(6) DEFAULT NULL,
  `ended_by_user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`es_meeting_agenda_activity_id`),
  KEY `ix_es_maa_meeting_open` (`es_meeting_id`,`ended_at`,`started_at`),
  KEY `ix_es_maa_agenda_item` (`es_meeting_agenda_item_id`,`started_at`),
  KEY `ix_es_maa_topic` (`es_topic_id`,`started_at`),
  KEY `fk_es_maa_started_by` (`started_by_user_id`),
  KEY `fk_es_maa_ended_by` (`ended_by_user_id`),
  CONSTRAINT `fk_es_maa_agenda_item` FOREIGN KEY (`es_meeting_agenda_item_id`) REFERENCES `es_meeting_agenda_item` (`es_meeting_agenda_item_id`),
  CONSTRAINT `fk_es_maa_ended_by` FOREIGN KEY (`ended_by_user_id`) REFERENCES `auth_user` (`user_id`),
  CONSTRAINT `fk_es_maa_meeting` FOREIGN KEY (`es_meeting_id`) REFERENCES `es_meeting` (`es_meeting_id`),
  CONSTRAINT `fk_es_maa_started_by` FOREIGN KEY (`started_by_user_id`) REFERENCES `auth_user` (`user_id`),
  CONSTRAINT `fk_es_maa_topic` FOREIGN KEY (`es_topic_id`) REFERENCES `es_topic` (`es_topic_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_meeting_agenda_item`
--

DROP TABLE IF EXISTS `es_meeting_agenda_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_meeting_agenda_item` (
  `es_meeting_agenda_item_id` bigint NOT NULL AUTO_INCREMENT,
  `accepted_at` datetime(6) DEFAULT NULL,
  `agenda_markdown` text,
  `created_at` datetime(6) NOT NULL,
  `display_order` int NOT NULL,
  `es_meeting_id` bigint NOT NULL,
  `es_topic_id` bigint DEFAULT NULL,
  `postponed_to_meeting_id` bigint DEFAULT NULL,
  `proposed_by_user_id` bigint DEFAULT NULL,
  `status` enum('ACCEPTED','CANCELLED','COVERED','DRAFT','NEEDS_REVISION','NOT_COVERED','POSTPONED','PROPOSED') NOT NULL,
  `status_note` text,
  `time_minutes` int DEFAULT NULL,
  `title` varchar(200) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`es_meeting_agenda_item_id`)
) ENGINE=InnoDB AUTO_INCREMENT=65 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_meeting_attendance`
--

DROP TABLE IF EXISTS `es_meeting_attendance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_meeting_attendance` (
  `es_meeting_attendance_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `es_topic_meeting_id` bigint unsigned NOT NULL,
  `attendance_date` date NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `first_name` varchar(100) NOT NULL,
  `last_name` varchar(100) DEFAULT NULL,
  `email` varchar(254) NOT NULL,
  `email_normalized` varchar(254) NOT NULL,
  `organization` varchar(200) DEFAULT NULL,
  `hope_text` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `es_meeting_id` bigint DEFAULT NULL,
  PRIMARY KEY (`es_meeting_attendance_id`),
  UNIQUE KEY `uq_attendance_meeting_date_email` (`es_topic_meeting_id`,`attendance_date`,`email_normalized`),
  KEY `ix_attendance_meeting_date` (`es_topic_meeting_id`,`attendance_date`),
  KEY `ix_attendance_user` (`user_id`),
  CONSTRAINT `fk_attendance_user` FOREIGN KEY (`user_id`) REFERENCES `auth_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=164 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_meeting_communication`
--

DROP TABLE IF EXISTS `es_meeting_communication`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_meeting_communication` (
  `es_meeting_communication_id` bigint NOT NULL AUTO_INCREMENT,
  `approved_at` datetime(6) DEFAULT NULL,
  `approved_by_user_id` bigint DEFAULT NULL,
  `cancellation_reason` text,
  `cancelled_at` datetime(6) DEFAULT NULL,
  `cancelled_by_user_id` bigint DEFAULT NULL,
  `communication_type` enum('CALL_FOR_TOPICS','CANCELLED','FINAL_AGENDA','PROPOSED_AGENDA','REMINDER') NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by_user_id` bigint NOT NULL,
  `es_meeting_id` bigint NOT NULL,
  `expected_meeting_status` enum('CANCELLED','COMPLETED','CLOSED','DRAFT','FINALIZED','IN_SESSION','PROPOSED') DEFAULT NULL,
  `include_general_members` bit(1) NOT NULL,
  `include_presenters` bit(1) NOT NULL,
  `include_topic_champions` bit(1) NOT NULL,
  `include_topic_subscribers` bit(1) NOT NULL,
  `last_error` text,
  `note_to_include` text,
  `scheduled_send_at` datetime(6) DEFAULT NULL,
  `sent_completed_at` datetime(6) DEFAULT NULL,
  `sent_started_at` datetime(6) DEFAULT NULL,
  `status` enum('CANCELLED','DRAFT','FAILED','SCHEDULED','SENDING','SENT') NOT NULL,
  `subject_override` varchar(500) DEFAULT NULL,
  `timezone_id` varchar(64) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`es_meeting_communication_id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_meeting_participant_count`
--

DROP TABLE IF EXISTS `es_meeting_participant_count`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_meeting_participant_count` (
  `es_meeting_participant_count_id` bigint NOT NULL AUTO_INCREMENT,
  `es_meeting_agenda_activity_id` bigint NOT NULL,
  `participant_count` int NOT NULL,
  `recorded_at` datetime(6) NOT NULL,
  `recorded_by_user_id` bigint NOT NULL,
  PRIMARY KEY (`es_meeting_participant_count_id`),
  KEY `ix_es_mpc_activity_recorded` (`es_meeting_agenda_activity_id`,`recorded_at`,`es_meeting_participant_count_id`),
  KEY `fk_es_mpc_recorded_by` (`recorded_by_user_id`),
  CONSTRAINT `fk_es_mpc_activity` FOREIGN KEY (`es_meeting_agenda_activity_id`) REFERENCES `es_meeting_agenda_activity` (`es_meeting_agenda_activity_id`),
  CONSTRAINT `fk_es_mpc_recorded_by` FOREIGN KEY (`recorded_by_user_id`) REFERENCES `auth_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_meeting_role_assignment`
--

DROP TABLE IF EXISTS `es_meeting_role_assignment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_meeting_role_assignment` (
  `es_meeting_role_assignment_id` bigint NOT NULL AUTO_INCREMENT,
  `es_meeting_id` bigint NOT NULL,
  `role_type` enum('CHAIR','SCRIBE') NOT NULL,
  `user_id` bigint NOT NULL,
  `started_at` datetime(6) NOT NULL,
  `ended_at` datetime(6) DEFAULT NULL,
  `assigned_by_user_id` bigint NOT NULL,
  PRIMARY KEY (`es_meeting_role_assignment_id`),
  KEY `ix_es_mra_meeting_role_open` (`es_meeting_id`,`role_type`,`ended_at`,`started_at`),
  KEY `ix_es_mra_user` (`user_id`,`started_at`),
  KEY `fk_es_mra_assigned_by` (`assigned_by_user_id`),
  CONSTRAINT `fk_es_mra_assigned_by` FOREIGN KEY (`assigned_by_user_id`) REFERENCES `auth_user` (`user_id`),
  CONSTRAINT `fk_es_mra_meeting` FOREIGN KEY (`es_meeting_id`) REFERENCES `es_meeting` (`es_meeting_id`),
  CONSTRAINT `fk_es_mra_user` FOREIGN KEY (`user_id`) REFERENCES `auth_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_meeting_status_history`
--

DROP TABLE IF EXISTS `es_meeting_status_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_meeting_status_history` (
  `es_meeting_status_history_id` bigint NOT NULL AUTO_INCREMENT,
  `es_meeting_id` bigint NOT NULL,
  `from_status` enum('CANCELLED','COMPLETED','CLOSED','DRAFT','FINALIZED','IN_SESSION','PROPOSED') DEFAULT NULL,
  `to_status` enum('CANCELLED','COMPLETED','CLOSED','DRAFT','FINALIZED','IN_SESSION','PROPOSED') NOT NULL,
  `changed_at` datetime(6) NOT NULL,
  `changed_by_user_id` bigint DEFAULT NULL,
  `transition_method` enum('AUTOMATIC','USER') NOT NULL,
  PRIMARY KEY (`es_meeting_status_history_id`),
  KEY `ix_es_msh_meeting_changed` (`es_meeting_id`,`changed_at`,`es_meeting_status_history_id`),
  KEY `ix_es_msh_to_status` (`to_status`,`changed_at`),
  KEY `fk_es_msh_changed_by` (`changed_by_user_id`),
  CONSTRAINT `fk_es_msh_changed_by` FOREIGN KEY (`changed_by_user_id`) REFERENCES `auth_user` (`user_id`),
  CONSTRAINT `fk_es_msh_meeting` FOREIGN KEY (`es_meeting_id`) REFERENCES `es_meeting` (`es_meeting_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_meeting_user_view`
--

DROP TABLE IF EXISTS `es_meeting_user_view`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_meeting_user_view` (
  `es_meeting_user_view_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `es_meeting_id` bigint NOT NULL,
  `first_viewed_at` datetime NOT NULL,
  `last_viewed_at` datetime NOT NULL,
  `visit_count` bigint unsigned NOT NULL DEFAULT '1',
  `last_counted_at` datetime NOT NULL,
  PRIMARY KEY (`es_meeting_user_view_id`),
  UNIQUE KEY `uq_es_meeting_user_view_user_meeting` (`user_id`,`es_meeting_id`),
  KEY `ix_es_meeting_user_view_user_recent` (`user_id`,`last_viewed_at`),
  KEY `ix_es_meeting_user_view_meeting_recent` (`es_meeting_id`,`last_viewed_at`),
  CONSTRAINT `fk_es_meeting_user_view_meeting` FOREIGN KEY (`es_meeting_id`) REFERENCES `es_meeting` (`es_meeting_id`),
  CONSTRAINT `fk_es_meeting_user_view_user` FOREIGN KEY (`user_id`) REFERENCES `auth_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_neighborhood`
--

DROP TABLE IF EXISTS `es_neighborhood`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_neighborhood` (
  `es_neighborhood_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `created_by_user_id` bigint NOT NULL,
  `description` text,
  `es_topic_space_id` bigint unsigned NOT NULL,
  `display_order` int NOT NULL,
  `is_active` bit(1) NOT NULL,
  `neighborhood_code` varchar(80) NOT NULL,
  `neighborhood_name` varchar(140) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`es_neighborhood_id`),
  UNIQUE KEY `UK_lltg11sfmdmahoduh6u9ggw2o` (`neighborhood_code`),
  UNIQUE KEY `uq_es_neighborhood_space_name` (`es_topic_space_id`,`neighborhood_name`),
  KEY `ix_es_neighborhood_topic_space` (`es_topic_space_id`),
  CONSTRAINT `fk_es_neighborhood_topic_space` FOREIGN KEY (`es_topic_space_id`) REFERENCES `es_topic_space` (`es_topic_space_id`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_recorded_outcome`
--

DROP TABLE IF EXISTS `es_recorded_outcome`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_recorded_outcome` (
  `es_recorded_outcome_id` bigint NOT NULL AUTO_INCREMENT,
  `es_topic_note_id` bigint NOT NULL,
  `source_node_id` varchar(128) NOT NULL,
  `outcome_type` enum('ACTION','DIRECTION','FORMAL_MOTION','OPEN_ISSUE','RATIONALE','WORKING_CONSENSUS') NOT NULL,
  `short_title` varchar(200) DEFAULT NULL,
  `outcome_text` text NOT NULL,
  `display_order` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by_user_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `updated_by_user_id` bigint NOT NULL,
  PRIMARY KEY (`es_recorded_outcome_id`),
  UNIQUE KEY `uq_es_ro_note_source` (`es_topic_note_id`,`source_node_id`),
  KEY `ix_es_ro_note_order` (`es_topic_note_id`,`display_order`,`es_recorded_outcome_id`),
  KEY `fk_es_ro_created_by` (`created_by_user_id`),
  KEY `fk_es_ro_updated_by` (`updated_by_user_id`),
  CONSTRAINT `fk_es_ro_created_by` FOREIGN KEY (`created_by_user_id`) REFERENCES `auth_user` (`user_id`),
  CONSTRAINT `fk_es_ro_note` FOREIGN KEY (`es_topic_note_id`) REFERENCES `es_topic_note` (`es_topic_note_id`),
  CONSTRAINT `fk_es_ro_updated_by` FOREIGN KEY (`updated_by_user_id`) REFERENCES `auth_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_subscription`
--

DROP TABLE IF EXISTS `es_subscription`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_subscription` (
  `es_subscription_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `email` varchar(254) NOT NULL,
  `email_normalized` varchar(254) NOT NULL,
  `es_topic_id` bigint DEFAULT NULL,
  `source_campaign_id` bigint DEFAULT NULL,
  `status` enum('SUBSCRIBED','CHAMPION','SUPPORT','UNSUBSCRIBED') NOT NULL DEFAULT 'SUBSCRIBED',
  `subscription_type` enum('GENERAL_ES','TOPIC') NOT NULL,
  `unsubscribe_token_hash` varbinary(32) DEFAULT NULL,
  `unsubscribed_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`es_subscription_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1292 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_survey`
--

DROP TABLE IF EXISTS `es_survey`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_survey` (
  `es_survey_id` bigint NOT NULL AUTO_INCREMENT,
  `closed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by_user_id` bigint DEFAULT NULL,
  `ready_at` datetime(6) DEFAULT NULL,
  `status` enum('ARCHIVED','CLOSED','DRAFT','READY') NOT NULL,
  `survey_description` text,
  `survey_key` varchar(80) DEFAULT NULL,
  `survey_name` varchar(160) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`es_survey_id`),
  UNIQUE KEY `UK_50k4tvbmkq7na087d3jnqpysy` (`survey_key`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_survey_answer`
--

DROP TABLE IF EXISTS `es_survey_answer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_survey_answer` (
  `es_survey_answer_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `es_survey_question_id` bigint NOT NULL,
  `es_survey_response_id` bigint NOT NULL,
  `numeric_value` int DEFAULT NULL,
  `text_value` text,
  PRIMARY KEY (`es_survey_answer_id`)
) ENGINE=InnoDB AUTO_INCREMENT=69 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_survey_question`
--

DROP TABLE IF EXISTS `es_survey_question`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_survey_question` (
  `es_survey_question_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `display_order` int NOT NULL,
  `es_survey_id` bigint NOT NULL,
  `question_text` text NOT NULL,
  `question_type` enum('LIKERT_1_5','TEXT') NOT NULL,
  `required` bit(1) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`es_survey_question_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_survey_response`
--

DROP TABLE IF EXISTS `es_survey_response`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_survey_response` (
  `es_survey_response_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `email` varchar(254) NOT NULL,
  `email_normalized` varchar(254) NOT NULL,
  `es_meeting_attendance_id` bigint DEFAULT NULL,
  `es_meeting_id` bigint DEFAULT NULL,
  `es_topic_meeting_survey_id` bigint NOT NULL,
  `first_name` varchar(100) DEFAULT NULL,
  `last_name` varchar(100) DEFAULT NULL,
  `organization` varchar(200) DEFAULT NULL,
  `submitted_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`es_survey_response_id`)
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_topic`
--

DROP TABLE IF EXISTS `es_topic`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_topic` (
  `es_topic_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `created_by_user_id` bigint NOT NULL,
  `description` text,
  `topic_summary` varchar(300) DEFAULT NULL,
  `search_keywords` text,
  `topic_emoji` varchar(64) DEFAULT NULL,
  `neighborhood` varchar(120) DEFAULT NULL,
  `policy_status` varchar(120) DEFAULT NULL,
  `priority_cdc` int NOT NULL,
  `priority_ehr` int NOT NULL,
  `priority_iis` int NOT NULL,
  `stage` varchar(80) DEFAULT NULL,
  `path` varchar(80) DEFAULT NULL,
  `es_topic_stage_definition_id` bigint unsigned DEFAULT NULL,
  `es_topic_path_definition_id` bigint unsigned DEFAULT NULL,
  `status` enum('ACTIVE','ARCHIVED','RETIRED') NOT NULL,
  `topic_code` varchar(80) NOT NULL,
  `topic_name` varchar(140) NOT NULL,
  `topic_type` varchar(120) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `confluence_url` varchar(500) DEFAULT NULL,
  `es_topic_space_id` bigint unsigned NOT NULL,
  PRIMARY KEY (`es_topic_id`),
  UNIQUE KEY `UK_49mf6h9vgqgfao3nfdvg4ib3d` (`topic_code`),
  KEY `ix_es_topic_space_id` (`es_topic_space_id`),
  KEY `ix_es_topic_stage_definition_id` (`es_topic_stage_definition_id`),
  KEY `ix_es_topic_path_definition_id` (`es_topic_path_definition_id`),
  CONSTRAINT `fk_es_topic_path_definition` FOREIGN KEY (`es_topic_path_definition_id`) REFERENCES `es_topic_path_definition` (`es_topic_path_definition_id`),
  CONSTRAINT `fk_es_topic_stage_definition` FOREIGN KEY (`es_topic_stage_definition_id`) REFERENCES `es_topic_stage_definition` (`es_topic_stage_definition_id`),
  CONSTRAINT `fk_es_topic_topic_space` FOREIGN KEY (`es_topic_space_id`) REFERENCES `es_topic_space` (`es_topic_space_id`)
) ENGINE=InnoDB AUTO_INCREMENT=137 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_topic_board_definition`
--

DROP TABLE IF EXISTS `es_topic_board_definition`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_topic_board_definition` (
  `es_topic_board_definition_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `board_code` varchar(80) NOT NULL,
  `board_name` varchar(140) NOT NULL,
  `board_description` text,
  `es_topic_space_id` bigint unsigned NOT NULL,
  `curator_topic_id` bigint DEFAULT NULL,
  `show_unassigned_stage` tinyint(1) NOT NULL DEFAULT '0',
  `show_unassigned_path` tinyint(1) NOT NULL DEFAULT '0',
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`es_topic_board_definition_id`),
  UNIQUE KEY `uq_es_topic_board_code` (`board_code`),
  KEY `ix_es_topic_board_space` (`es_topic_space_id`),
  KEY `ix_es_topic_board_curator` (`curator_topic_id`),
  CONSTRAINT `fk_es_topic_board_curator` FOREIGN KEY (`curator_topic_id`) REFERENCES `es_topic` (`es_topic_id`),
  CONSTRAINT `fk_es_topic_board_space` FOREIGN KEY (`es_topic_space_id`) REFERENCES `es_topic_space` (`es_topic_space_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_topic_board_path`
--

DROP TABLE IF EXISTS `es_topic_board_path`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_topic_board_path` (
  `es_topic_board_path_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `es_topic_board_definition_id` bigint unsigned NOT NULL,
  `es_topic_path_definition_id` bigint unsigned NOT NULL,
  `display_order` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`es_topic_board_path_id`),
  UNIQUE KEY `uq_es_topic_board_path` (`es_topic_board_definition_id`,`es_topic_path_definition_id`),
  KEY `ix_es_topic_board_path_order` (`es_topic_board_definition_id`,`display_order`),
  KEY `fk_es_topic_board_path_definition` (`es_topic_path_definition_id`),
  CONSTRAINT `fk_es_topic_board_path_board` FOREIGN KEY (`es_topic_board_definition_id`) REFERENCES `es_topic_board_definition` (`es_topic_board_definition_id`),
  CONSTRAINT `fk_es_topic_board_path_definition` FOREIGN KEY (`es_topic_path_definition_id`) REFERENCES `es_topic_path_definition` (`es_topic_path_definition_id`)
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_topic_board_stage`
--

DROP TABLE IF EXISTS `es_topic_board_stage`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_topic_board_stage` (
  `es_topic_board_stage_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `es_topic_board_definition_id` bigint unsigned NOT NULL,
  `es_topic_stage_definition_id` bigint unsigned NOT NULL,
  `display_order` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`es_topic_board_stage_id`),
  UNIQUE KEY `uq_es_topic_board_stage` (`es_topic_board_definition_id`,`es_topic_stage_definition_id`),
  KEY `ix_es_topic_board_stage_order` (`es_topic_board_definition_id`,`display_order`),
  KEY `fk_es_topic_board_stage_definition` (`es_topic_stage_definition_id`),
  CONSTRAINT `fk_es_topic_board_stage_board` FOREIGN KEY (`es_topic_board_definition_id`) REFERENCES `es_topic_board_definition` (`es_topic_board_definition_id`),
  CONSTRAINT `fk_es_topic_board_stage_definition` FOREIGN KEY (`es_topic_stage_definition_id`) REFERENCES `es_topic_stage_definition` (`es_topic_stage_definition_id`)
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_topic_curation`
--

DROP TABLE IF EXISTS `es_topic_curation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_topic_curation` (
  `es_topic_curation_id` bigint NOT NULL AUTO_INCREMENT,
  `category_label` varchar(80) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by_user_id` bigint NOT NULL,
  `curated_topic_id` bigint NOT NULL,
  `curation_status` varchar(80) DEFAULT NULL,
  `curator_topic_id` bigint NOT NULL,
  `display_order` int NOT NULL,
  `editorial_note` text,
  `topic_alias` varchar(140) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `agenda_cadence_days` int DEFAULT NULL,
  PRIMARY KEY (`es_topic_curation_id`)
) ENGINE=InnoDB AUTO_INCREMENT=41 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_topic_meeting`
--

DROP TABLE IF EXISTS `es_topic_meeting`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_topic_meeting` (
  `es_topic_meeting_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `disabled_at` datetime(6) DEFAULT NULL,
  `disabled_by_user_id` bigint DEFAULT NULL,
  `es_topic_id` bigint NOT NULL,
  `join_requires_approval` bit(1) NOT NULL,
  `meeting_description` text,
  `meeting_name` varchar(160) NOT NULL,
  `status` enum('ACTIVE','DISABLED') NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `online_meeting_details` text,
  `online_meeting_url` varchar(2048) DEFAULT NULL,
  PRIMARY KEY (`es_topic_meeting_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_topic_meeting_cochair`
--

DROP TABLE IF EXISTS `es_topic_meeting_cochair`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_topic_meeting_cochair` (
  `es_topic_meeting_cochair_id` bigint NOT NULL AUTO_INCREMENT,
  `es_topic_meeting_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `status` enum('ACTIVE','INACTIVE') NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by_user_id` bigint NOT NULL,
  `inactive_at` datetime(6) DEFAULT NULL,
  `inactive_by_user_id` bigint DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`es_topic_meeting_cochair_id`),
  KEY `ix_es_tm_cchair_meeting_status` (`es_topic_meeting_id`,`status`,`created_at`),
  KEY `ix_es_tm_cchair_user` (`user_id`,`created_at`),
  KEY `fk_es_tm_cchair_created_by` (`created_by_user_id`),
  KEY `fk_es_tm_cchair_inactive_by` (`inactive_by_user_id`),
  CONSTRAINT `fk_es_tm_cchair_created_by` FOREIGN KEY (`created_by_user_id`) REFERENCES `auth_user` (`user_id`),
  CONSTRAINT `fk_es_tm_cchair_inactive_by` FOREIGN KEY (`inactive_by_user_id`) REFERENCES `auth_user` (`user_id`),
  CONSTRAINT `fk_es_tm_cchair_meeting` FOREIGN KEY (`es_topic_meeting_id`) REFERENCES `es_topic_meeting` (`es_topic_meeting_id`),
  CONSTRAINT `fk_es_tm_cchair_user` FOREIGN KEY (`user_id`) REFERENCES `auth_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_topic_meeting_member`
--

DROP TABLE IF EXISTS `es_topic_meeting_member`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_topic_meeting_member` (
  `es_topic_meeting_member_id` bigint NOT NULL AUTO_INCREMENT,
  `approved_at` datetime(6) DEFAULT NULL,
  `approved_by_user_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `email` varchar(254) NOT NULL,
  `email_normalized` varchar(254) NOT NULL,
  `es_topic_meeting_id` bigint NOT NULL,
  `membership_status` enum('APPROVED','DECLINED','REMOVED','REQUESTED') NOT NULL,
  `source_campaign_id` bigint DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`es_topic_meeting_member_id`)
) ENGINE=InnoDB AUTO_INCREMENT=73 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_topic_meeting_poll`
--

DROP TABLE IF EXISTS `es_topic_meeting_poll`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_topic_meeting_poll` (
  `es_topic_meeting_poll_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `default_timezone` varchar(80) NOT NULL,
  `es_topic_meeting_id` bigint NOT NULL,
  `poll_description` text,
  `poll_name` varchar(160) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`es_topic_meeting_poll_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_topic_meeting_poll_option`
--

DROP TABLE IF EXISTS `es_topic_meeting_poll_option`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_topic_meeting_poll_option` (
  `es_topic_meeting_poll_option_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `display_order` int NOT NULL,
  `ends_at_utc` datetime(6) DEFAULT NULL,
  `es_topic_meeting_poll_id` bigint NOT NULL,
  `starts_at_utc` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`es_topic_meeting_poll_option_id`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_topic_meeting_poll_response`
--

DROP TABLE IF EXISTS `es_topic_meeting_poll_response`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_topic_meeting_poll_response` (
  `es_topic_meeting_poll_response_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `es_topic_meeting_poll_option_id` bigint NOT NULL,
  `response` enum('MAYBE','NO','YES') NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`es_topic_meeting_poll_response_id`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_topic_meeting_survey`
--

DROP TABLE IF EXISTS `es_topic_meeting_survey`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_topic_meeting_survey` (
  `es_topic_meeting_survey_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `created_by_user_id` bigint DEFAULT NULL,
  `end_date` date NOT NULL,
  `es_survey_id` bigint NOT NULL,
  `es_topic_meeting_id` bigint NOT NULL,
  `start_date` date NOT NULL,
  `status` enum('ACTIVE','CLOSED','PAUSED') NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`es_topic_meeting_survey_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_topic_neighborhood`
--

DROP TABLE IF EXISTS `es_topic_neighborhood`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_topic_neighborhood` (
  `es_topic_neighborhood_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `es_topic_id` bigint NOT NULL,
  `es_neighborhood_id` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`es_topic_neighborhood_id`),
  UNIQUE KEY `uq_es_topic_neighborhood` (`es_topic_id`,`es_neighborhood_id`),
  KEY `ix_es_topic_neighborhood_topic` (`es_topic_id`),
  KEY `ix_es_topic_neighborhood_neighborhood` (`es_neighborhood_id`)
) ENGINE=InnoDB AUTO_INCREMENT=309 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_topic_note`
--

DROP TABLE IF EXISTS `es_topic_note`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_topic_note` (
  `es_topic_note_id` bigint NOT NULL AUTO_INCREMENT,
  `es_topic_id` bigint NOT NULL,
  `es_meeting_id` bigint DEFAULT NULL,
  `es_meeting_agenda_item_id` bigint DEFAULT NULL,
  `note_title` varchar(200) DEFAULT NULL,
  `document_json` json NOT NULL,
  `document_text` longtext,
  `revision_no` bigint NOT NULL,
  `status` enum('FINALIZED','OPEN') NOT NULL,
  `active_editor_user_id` bigint DEFAULT NULL,
  `active_editor_started_at` datetime(6) DEFAULT NULL,
  `active_editor_version` bigint NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL,
  `created_by_user_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `finalize_at` datetime(6) DEFAULT NULL,
  `finalized_at` datetime(6) DEFAULT NULL,
  `finalized_by_user_id` bigint DEFAULT NULL,
  `finalization_method` enum('AUTOMATIC','MANUAL') DEFAULT NULL,
  PRIMARY KEY (`es_topic_note_id`),
  KEY `ix_es_topic_note_topic_created` (`es_topic_id`,`created_at`,`es_topic_note_id`),
  KEY `ix_es_topic_note_meeting_status` (`es_meeting_id`,`status`,`finalize_at`,`es_topic_note_id`),
  KEY `ix_es_topic_note_agenda_status` (`es_meeting_agenda_item_id`,`status`,`es_topic_note_id`),
  KEY `ix_es_topic_note_finalize_at` (`finalize_at`,`es_topic_note_id`),
  KEY `fk_es_topic_note_created_by` (`created_by_user_id`),
  KEY `fk_es_topic_note_finalized_by` (`finalized_by_user_id`),
  KEY `fk_es_topic_note_active_editor` (`active_editor_user_id`),
  CONSTRAINT `fk_es_topic_note_active_editor` FOREIGN KEY (`active_editor_user_id`) REFERENCES `auth_user` (`user_id`),
  CONSTRAINT `fk_es_topic_note_agenda_item` FOREIGN KEY (`es_meeting_agenda_item_id`) REFERENCES `es_meeting_agenda_item` (`es_meeting_agenda_item_id`),
  CONSTRAINT `fk_es_topic_note_created_by` FOREIGN KEY (`created_by_user_id`) REFERENCES `auth_user` (`user_id`),
  CONSTRAINT `fk_es_topic_note_finalized_by` FOREIGN KEY (`finalized_by_user_id`) REFERENCES `auth_user` (`user_id`),
  CONSTRAINT `fk_es_topic_note_meeting` FOREIGN KEY (`es_meeting_id`) REFERENCES `es_meeting` (`es_meeting_id`),
  CONSTRAINT `fk_es_topic_note_topic` FOREIGN KEY (`es_topic_id`) REFERENCES `es_topic` (`es_topic_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_topic_note_editor_history`
--

DROP TABLE IF EXISTS `es_topic_note_editor_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_topic_note_editor_history` (
  `es_topic_note_editor_history_id` bigint NOT NULL AUTO_INCREMENT,
  `es_topic_note_id` bigint NOT NULL,
  `previous_editor_user_id` bigint DEFAULT NULL,
  `new_editor_user_id` bigint NOT NULL,
  `changed_at` datetime(6) NOT NULL,
  `changed_by_user_id` bigint NOT NULL,
  PRIMARY KEY (`es_topic_note_editor_history_id`),
  KEY `ix_es_tneh_note_changed` (`es_topic_note_id`,`changed_at`,`es_topic_note_editor_history_id`),
  KEY `fk_es_tneh_previous_editor` (`previous_editor_user_id`),
  KEY `fk_es_tneh_new_editor` (`new_editor_user_id`),
  KEY `fk_es_tneh_changed_by` (`changed_by_user_id`),
  CONSTRAINT `fk_es_tneh_changed_by` FOREIGN KEY (`changed_by_user_id`) REFERENCES `auth_user` (`user_id`),
  CONSTRAINT `fk_es_tneh_new_editor` FOREIGN KEY (`new_editor_user_id`) REFERENCES `auth_user` (`user_id`),
  CONSTRAINT `fk_es_tneh_note` FOREIGN KEY (`es_topic_note_id`) REFERENCES `es_topic_note` (`es_topic_note_id`),
  CONSTRAINT `fk_es_tneh_previous_editor` FOREIGN KEY (`previous_editor_user_id`) REFERENCES `auth_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_topic_note_revision`
--

DROP TABLE IF EXISTS `es_topic_note_revision`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_topic_note_revision` (
  `es_topic_note_revision_id` bigint NOT NULL AUTO_INCREMENT,
  `es_topic_note_id` bigint NOT NULL,
  `revision_no` bigint NOT NULL,
  `document_json` json NOT NULL,
  `document_text` longtext,
  `saved_at` datetime(6) NOT NULL,
  `saved_by_user_id` bigint NOT NULL,
  PRIMARY KEY (`es_topic_note_revision_id`),
  UNIQUE KEY `uq_es_tnr_note_revision` (`es_topic_note_id`,`revision_no`),
  KEY `ix_es_tnr_note_saved` (`es_topic_note_id`,`revision_no`,`es_topic_note_revision_id`),
  KEY `fk_es_tnr_saved_by` (`saved_by_user_id`),
  CONSTRAINT `fk_es_tnr_note` FOREIGN KEY (`es_topic_note_id`) REFERENCES `es_topic_note` (`es_topic_note_id`),
  CONSTRAINT `fk_es_tnr_saved_by` FOREIGN KEY (`saved_by_user_id`) REFERENCES `auth_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_topic_path_definition`
--

DROP TABLE IF EXISTS `es_topic_path_definition`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_topic_path_definition` (
  `es_topic_path_definition_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `es_topic_space_id` bigint unsigned NOT NULL,
  `path_code` varchar(80) NOT NULL,
  `path_name` varchar(120) NOT NULL,
  `path_description` text,
  `display_order` int NOT NULL DEFAULT '0',
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`es_topic_path_definition_id`),
  UNIQUE KEY `uq_es_topic_path_space_code` (`es_topic_space_id`,`path_code`),
  UNIQUE KEY `uq_es_topic_path_space_name` (`es_topic_space_id`,`path_name`),
  KEY `ix_es_topic_path_space_active_order` (`es_topic_space_id`,`is_active`,`display_order`,`path_name`),
  CONSTRAINT `fk_es_topic_path_space` FOREIGN KEY (`es_topic_space_id`) REFERENCES `es_topic_space` (`es_topic_space_id`)
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_topic_relationship`
--

DROP TABLE IF EXISTS `es_topic_relationship`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_topic_relationship` (
  `es_topic_relationship_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `created_by_user_id` bigint NOT NULL,
  `display_order` int NOT NULL,
  `from_topic_id` bigint NOT NULL,
  `relationship_type` enum('BLOCKER_FOR','DEPENDS_ON','DERIVED_FROM','DUPLICATE_OF','FEEDS_INTO','OPERATIONALIZES','OVERLAPS','RELATED_TO','SUPERSEDES') NOT NULL,
  `to_topic_id` bigint NOT NULL,
  PRIMARY KEY (`es_topic_relationship_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_topic_review`
--

DROP TABLE IF EXISTS `es_topic_review`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_topic_review` (
  `es_topic_review_id` bigint NOT NULL AUTO_INCREMENT,
  `community_value_score` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `es_campaign_id` bigint NOT NULL,
  `es_topic_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`es_topic_review_id`)
) ENGINE=InnoDB AUTO_INCREMENT=690 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_topic_space`
--

DROP TABLE IF EXISTS `es_topic_space`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_topic_space` (
  `es_topic_space_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `space_code` varchar(80) NOT NULL,
  `space_name` varchar(140) NOT NULL,
  `description` text,
  `stage_concept_description` text,
  `path_concept_description` text,
  `visibility` enum('PUBLIC','PRIVATE') NOT NULL,
  `display_order` int NOT NULL DEFAULT '0',
  `primary_es_topic_board_definition_id` bigint unsigned DEFAULT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`es_topic_space_id`),
  UNIQUE KEY `uq_es_topic_space_code` (`space_code`),
  KEY `ix_es_topic_space_visible_order` (`visibility`,`is_active`,`display_order`,`space_name`),
  KEY `ix_es_topic_space_primary_board` (`primary_es_topic_board_definition_id`),
  CONSTRAINT `fk_es_topic_space_primary_board` FOREIGN KEY (`primary_es_topic_board_definition_id`) REFERENCES `es_topic_board_definition` (`es_topic_board_definition_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_topic_space_member`
--

DROP TABLE IF EXISTS `es_topic_space_member`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_topic_space_member` (
  `es_topic_space_member_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `es_topic_space_id` bigint unsigned NOT NULL,
  `user_id` bigint NOT NULL,
  `role` enum('MEMBER','ADMIN') NOT NULL DEFAULT 'MEMBER',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`es_topic_space_member_id`),
  UNIQUE KEY `uq_es_topic_space_member_space_user` (`es_topic_space_id`,`user_id`),
  KEY `ix_es_topic_space_member_user` (`user_id`),
  KEY `ix_es_topic_space_member_space_role` (`es_topic_space_id`,`role`),
  CONSTRAINT `fk_es_topic_space_member_space` FOREIGN KEY (`es_topic_space_id`) REFERENCES `es_topic_space` (`es_topic_space_id`),
  CONSTRAINT `fk_es_topic_space_member_user` FOREIGN KEY (`user_id`) REFERENCES `auth_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_topic_stage_definition`
--

DROP TABLE IF EXISTS `es_topic_stage_definition`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_topic_stage_definition` (
  `es_topic_stage_definition_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `es_topic_space_id` bigint unsigned NOT NULL,
  `stage_code` varchar(80) NOT NULL,
  `stage_name` varchar(120) NOT NULL,
  `stage_description` text,
  `display_order` int NOT NULL DEFAULT '0',
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`es_topic_stage_definition_id`),
  UNIQUE KEY `uq_es_topic_stage_space_code` (`es_topic_space_id`,`stage_code`),
  UNIQUE KEY `uq_es_topic_stage_space_name` (`es_topic_space_id`,`stage_name`),
  KEY `ix_es_topic_stage_space_active_order` (`es_topic_space_id`,`is_active`,`display_order`,`stage_name`),
  CONSTRAINT `fk_es_topic_stage_space` FOREIGN KEY (`es_topic_space_id`) REFERENCES `es_topic_space` (`es_topic_space_id`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `es_topic_user_view`
--

DROP TABLE IF EXISTS `es_topic_user_view`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `es_topic_user_view` (
  `es_topic_user_view_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `es_topic_id` bigint NOT NULL,
  `first_viewed_at` datetime(6) NOT NULL,
  `last_viewed_at` datetime(6) NOT NULL,
  `visit_count` bigint unsigned NOT NULL DEFAULT '1',
  `last_counted_at` datetime(6) NOT NULL,
  PRIMARY KEY (`es_topic_user_view_id`),
  UNIQUE KEY `uq_es_topic_user_view_user_topic` (`user_id`,`es_topic_id`),
  KEY `ix_es_topic_user_view_user_recent` (`user_id`,`last_viewed_at`),
  KEY `ix_es_topic_user_view_topic_recent` (`es_topic_id`,`last_viewed_at`),
  CONSTRAINT `fk_es_topic_user_view_topic` FOREIGN KEY (`es_topic_id`) REFERENCES `es_topic` (`es_topic_id`),
  CONSTRAINT `fk_es_topic_user_view_user` FOREIGN KEY (`user_id`) REFERENCES `auth_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `hub_settings`
--

DROP TABLE IF EXISTS `hub_settings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `hub_settings` (
  `setting_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `external_base_url` varchar(300) NOT NULL,
  `smtp_host` varchar(255) NOT NULL,
  `smtp_port` int NOT NULL,
  `smtp_username` varchar(255) NOT NULL,
  `smtp_password` varchar(255) NOT NULL,
  `smtp_auth` tinyint(1) NOT NULL DEFAULT '1',
  `smtp_starttls` tinyint(1) NOT NULL DEFAULT '1',
  `smtp_ssl` tinyint(1) NOT NULL DEFAULT '0',
  `smtp_from_email` varchar(254) NOT NULL,
  `smtp_from_name` varchar(160) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `email_enabled` bit(1) NOT NULL,
  PRIMARY KEY (`setting_id`),
  KEY `ix_hub_settings_active` (`active`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ig_topic`
--

DROP TABLE IF EXISTS `ig_topic`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ig_topic` (
  `topic_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `topic_code` varchar(80) NOT NULL,
  `topic_name` varchar(140) NOT NULL,
  `description` text,
  `created_by_user_id` bigint NOT NULL,
  `status` enum('ACTIVE','ARCHIVED') NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`topic_id`),
  UNIQUE KEY `uq_topic_code` (`topic_code`),
  KEY `ix_topic_status` (`status`),
  KEY `fk_topic_creator` (`created_by_user_id`),
  CONSTRAINT `fk_topic_creator` FOREIGN KEY (`created_by_user_id`) REFERENCES `auth_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `legal_term`
--

DROP TABLE IF EXISTS `legal_term`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `legal_term` (
  `term_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `term_code` varchar(80) NOT NULL,
  `version_num` int NOT NULL,
  `title` varchar(200) NOT NULL,
  `short_text` varchar(500) NOT NULL,
  `full_text` text,
  `full_text_url` varchar(500) DEFAULT NULL,
  `scope_type` enum('REGISTRATION','WORKSPACE','BOTH') NOT NULL DEFAULT 'REGISTRATION',
  `is_required` tinyint(1) NOT NULL DEFAULT '1',
  `display_order` int NOT NULL DEFAULT '0',
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `effective_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `retired_at` datetime DEFAULT NULL,
  `created_by_user_id` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`term_id`),
  UNIQUE KEY `uq_term_version` (`term_code`,`version_num`),
  KEY `ix_term_scope_active` (`scope_type`,`is_active`,`effective_at`),
  KEY `ix_term_display` (`scope_type`,`display_order`),
  KEY `fk_legal_term_creator` (`created_by_user_id`),
  CONSTRAINT `fk_legal_term_creator` FOREIGN KEY (`created_by_user_id`) REFERENCES `auth_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `legal_term_acceptance`
--

DROP TABLE IF EXISTS `legal_term_acceptance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `legal_term_acceptance` (
  `acceptance_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `term_id` bigint unsigned NOT NULL,
  `user_id` bigint NOT NULL,
  `workspace_id` bigint unsigned DEFAULT NULL,
  `accepted_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `accepted_value` tinyint(1) NOT NULL DEFAULT '1',
  `ip_address` varchar(45) DEFAULT NULL,
  `user_agent` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`acceptance_id`),
  UNIQUE KEY `uq_term_acceptance_once` (`term_id`,`user_id`,`workspace_id`),
  KEY `ix_accept_user` (`user_id`,`accepted_at`),
  KEY `ix_accept_term` (`term_id`,`accepted_at`),
  KEY `ix_accept_workspace` (`workspace_id`,`user_id`),
  CONSTRAINT `fk_term_acceptance_term` FOREIGN KEY (`term_id`) REFERENCES `legal_term` (`term_id`),
  CONSTRAINT `fk_term_acceptance_user` FOREIGN KEY (`user_id`) REFERENCES `auth_user` (`user_id`),
  CONSTRAINT `fk_term_acceptance_workspace` FOREIGN KEY (`workspace_id`) REFERENCES `connect_workspace` (`workspace_id`)
) ENGINE=InnoDB AUTO_INCREMENT=229 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `usage_daily_agg`
--

DROP TABLE IF EXISTS `usage_daily_agg`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `usage_daily_agg` (
  `app_id` bigint NOT NULL,
  `metric` enum('API_CALL','API_ERROR_4XX','API_ERROR_5XX') NOT NULL,
  `token_id` bigint NOT NULL,
  `usage_day` date NOT NULL,
  `count_value` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`app_id`,`metric`,`token_id`,`usage_day`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Temporary view structure for view `v_email_prospect`
--

DROP TABLE IF EXISTS `v_email_prospect`;
/*!50001 DROP VIEW IF EXISTS `v_email_prospect`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `v_email_prospect` AS SELECT 
 1 AS `email_normalized`,
 1 AS `first_contact_at`,
 1 AS `last_contact_at`,
 1 AS `campaign_registration_count`,
 1 AS `comment_count`,
 1 AS `subscription_count`,
 1 AS `meeting_member_count`,
 1 AS `meeting_attendance_count`*/;
SET character_set_client = @saved_cs_client;

--
-- Table structure for table `workspace_app`
--

DROP TABLE IF EXISTS `workspace_app`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `workspace_app` (
  `workspace_app_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `workspace_id` bigint unsigned NOT NULL,
  `app_id` bigint unsigned NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`workspace_app_id`),
  UNIQUE KEY `uq_workspace_app` (`workspace_id`,`app_id`),
  KEY `fk_workspace_app_app` (`app_id`),
  CONSTRAINT `fk_workspace_app_app` FOREIGN KEY (`app_id`) REFERENCES `app_registry` (`app_id`),
  CONSTRAINT `fk_workspace_app_workspace` FOREIGN KEY (`workspace_id`) REFERENCES `connect_workspace` (`workspace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `workspace_endpoint`
--

DROP TABLE IF EXISTS `workspace_endpoint`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `workspace_endpoint` (
  `endpoint_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `system_id` bigint unsigned NOT NULL,
  `endpoint_type` enum('FHIR_BASE','SMART_CONFIG','WEBHOOK','OTHER') NOT NULL DEFAULT 'FHIR_BASE',
  `url` varchar(500) DEFAULT NULL,
  `auth_type` enum('AIRA_TOKEN','BEARER_PAT','NONE','OTHER') NOT NULL DEFAULT 'BEARER_PAT',
  `auth_instructions` text,
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`endpoint_id`),
  KEY `ix_endpoint_system` (`system_id`,`is_active`),
  CONSTRAINT `fk_endpoint_system` FOREIGN KEY (`system_id`) REFERENCES `workspace_system` (`system_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `workspace_enrollment`
--

DROP TABLE IF EXISTS `workspace_enrollment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `workspace_enrollment` (
  `enrollment_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `workspace_id` bigint unsigned NOT NULL,
  `user_id` bigint NOT NULL,
  `state` enum('PENDING','APPROVED','REJECTED','SUSPENDED') NOT NULL DEFAULT 'PENDING',
  `consent_at` datetime DEFAULT NULL,
  `approved_by_user_id` bigint DEFAULT NULL,
  `approved_at` datetime DEFAULT NULL,
  `admin_note` varchar(400) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`enrollment_id`),
  UNIQUE KEY `uq_workspace_user` (`workspace_id`,`user_id`),
  KEY `ix_enrollment_state` (`workspace_id`,`state`),
  KEY `fk_enroll_user` (`user_id`),
  KEY `fk_enroll_approver` (`approved_by_user_id`),
  CONSTRAINT `fk_enroll_approver` FOREIGN KEY (`approved_by_user_id`) REFERENCES `auth_user` (`user_id`),
  CONSTRAINT `fk_enroll_user` FOREIGN KEY (`user_id`) REFERENCES `auth_user` (`user_id`),
  CONSTRAINT `fk_enroll_workspace` FOREIGN KEY (`workspace_id`) REFERENCES `connect_workspace` (`workspace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `workspace_progress`
--

DROP TABLE IF EXISTS `workspace_progress`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `workspace_progress` (
  `progress_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `workspace_id` bigint unsigned NOT NULL,
  `step_id` bigint unsigned NOT NULL,
  `client_system_id` bigint unsigned NOT NULL,
  `server_system_id` bigint unsigned NOT NULL,
  `status` enum('NO_PROGRESS','PROBLEMS','PARTIAL','WORKS','NOT_APPLICABLE') NOT NULL DEFAULT 'NO_PROGRESS',
  `note` varchar(800) DEFAULT NULL,
  `reported_by_user_id` bigint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`progress_id`),
  UNIQUE KEY `uq_progress_cell` (`step_id`,`client_system_id`,`server_system_id`),
  KEY `ix_progress_workspace` (`workspace_id`,`client_system_id`,`server_system_id`),
  KEY `ix_progress_status` (`workspace_id`,`status`),
  KEY `fk_progress_client` (`client_system_id`),
  KEY `fk_progress_server` (`server_system_id`),
  KEY `fk_progress_reporter` (`reported_by_user_id`),
  CONSTRAINT `fk_progress_client` FOREIGN KEY (`client_system_id`) REFERENCES `workspace_system` (`system_id`),
  CONSTRAINT `fk_progress_reporter` FOREIGN KEY (`reported_by_user_id`) REFERENCES `auth_user` (`user_id`),
  CONSTRAINT `fk_progress_server` FOREIGN KEY (`server_system_id`) REFERENCES `workspace_system` (`system_id`),
  CONSTRAINT `fk_progress_step` FOREIGN KEY (`step_id`) REFERENCES `workspace_step` (`step_id`),
  CONSTRAINT `fk_progress_workspace` FOREIGN KEY (`workspace_id`) REFERENCES `connect_workspace` (`workspace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `workspace_step`
--

DROP TABLE IF EXISTS `workspace_step`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `workspace_step` (
  `step_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `workspace_id` bigint unsigned NOT NULL,
  `step_name` varchar(140) NOT NULL,
  `applies_to` enum('CLIENT_TO_SERVER','CLIENT_ONLY','SERVER_ONLY','BOTH') NOT NULL DEFAULT 'CLIENT_TO_SERVER',
  `sort_order` int NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`step_id`),
  KEY `ix_step_workspace` (`workspace_id`,`sort_order`),
  CONSTRAINT `fk_step_workspace` FOREIGN KEY (`workspace_id`) REFERENCES `connect_workspace` (`workspace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `workspace_system`
--

DROP TABLE IF EXISTS `workspace_system`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `workspace_system` (
  `system_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `workspace_id` bigint unsigned NOT NULL,
  `system_name` varchar(160) NOT NULL,
  `managed_by` enum('AIRA','THIRD_PARTY') NOT NULL DEFAULT 'THIRD_PARTY',
  `capability` enum('CLIENT','SERVER','BOTH') NOT NULL,
  `availability` enum('UP','DOWN','INTERMITTENT','UNKNOWN') NOT NULL DEFAULT 'UNKNOWN',
  `availability_note` varchar(400) DEFAULT NULL,
  `description` text,
  `how_to_use` text,
  `limitations` text,
  `created_by_user_id` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`system_id`),
  KEY `ix_system_workspace` (`workspace_id`,`capability`,`managed_by`),
  KEY `ix_system_availability` (`workspace_id`,`availability`),
  KEY `fk_system_creator` (`created_by_user_id`),
  CONSTRAINT `fk_system_creator` FOREIGN KEY (`created_by_user_id`) REFERENCES `auth_user` (`user_id`),
  CONSTRAINT `fk_system_workspace` FOREIGN KEY (`workspace_id`) REFERENCES `connect_workspace` (`workspace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `workspace_system_contact`
--

DROP TABLE IF EXISTS `workspace_system_contact`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `workspace_system_contact` (
  `system_contact_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `system_id` bigint unsigned NOT NULL,
  `user_id` bigint NOT NULL,
  `contact_role` varchar(120) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`system_contact_id`),
  UNIQUE KEY `uq_system_user` (`system_id`,`user_id`),
  KEY `ix_system_contact_user` (`user_id`),
  CONSTRAINT `fk_sys_contact_system` FOREIGN KEY (`system_id`) REFERENCES `workspace_system` (`system_id`),
  CONSTRAINT `fk_sys_contact_user` FOREIGN KEY (`user_id`) REFERENCES `auth_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping events for database 'interophub'
--

--
-- Dumping routines for database 'interophub'
--

--
-- Final view structure for view `v_email_prospect`
--

/*!50001 DROP VIEW IF EXISTS `v_email_prospect`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `v_email_prospect` AS select `t`.`email_normalized` AS `email_normalized`,min(`t`.`created_at`) AS `first_contact_at`,max(`t`.`created_at`) AS `last_contact_at`,sum(`t`.`src_campaign_reg`) AS `campaign_registration_count`,sum(`t`.`src_comment`) AS `comment_count`,sum(`t`.`src_subscription`) AS `subscription_count`,sum(`t`.`src_meeting_member`) AS `meeting_member_count`,sum(`t`.`src_meeting_attendance`) AS `meeting_attendance_count` from (select `es_campaign_registration`.`email_normalized` AS `email_normalized`,`es_campaign_registration`.`created_at` AS `created_at`,1 AS `1`,0 AS `0`,0 AS `0`,0 AS `0`,0 AS `0` from `es_campaign_registration` where (`es_campaign_registration`.`email_normalized` is not null) union all select `es_comment`.`email_normalized` AS `email_normalized`,`es_comment`.`created_at` AS `created_at`,0 AS `0`,1 AS `1`,0 AS `0`,0 AS `0`,0 AS `0` from `es_comment` where ((`es_comment`.`email_normalized` is not null) and (`es_comment`.`user_id` is null)) union all select `es_subscription`.`email_normalized` AS `email_normalized`,`es_subscription`.`created_at` AS `created_at`,0 AS `0`,0 AS `0`,1 AS `1`,0 AS `0`,0 AS `0` from `es_subscription` where ((`es_subscription`.`email_normalized` is not null) and (`es_subscription`.`user_id` is null)) union all select `es_topic_meeting_member`.`email_normalized` AS `email_normalized`,`es_topic_meeting_member`.`created_at` AS `created_at`,0 AS `0`,0 AS `0`,0 AS `0`,1 AS `1`,0 AS `0` from `es_topic_meeting_member` where ((`es_topic_meeting_member`.`email_normalized` is not null) and (`es_topic_meeting_member`.`user_id` is null)) union all select `es_meeting_attendance`.`email_normalized` AS `email_normalized`,`es_meeting_attendance`.`created_at` AS `created_at`,0 AS `0`,0 AS `0`,0 AS `0`,0 AS `0`,1 AS `1` from `es_meeting_attendance` where ((`es_meeting_attendance`.`email_normalized` is not null) and (`es_meeting_attendance`.`user_id` is null))) `t` (`email_normalized`,`created_at`,`src_campaign_reg`,`src_comment`,`src_subscription`,`src_meeting_member`,`src_meeting_attendance`) where `t`.`email_normalized` in (select `auth_user`.`email_normalized` from `auth_user` where (`auth_user`.`status` <> 'DELETED')) is false group by `t`.`email_normalized` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-08-10  5:30:25
-- ============================================================
-- AUTO-GENERATED FILE — DO NOT HAND-EDIT
-- Generated by T:\scripts\python\refresh_interophub_db.py /
--           T:\scripts\python\restore_interophub_db_from_latest_local.py
-- via `mysqldump --no-data` against the local `interophub` database,
-- immediately after applying db/local_database_refresh.sql and
-- db/unapplied_updates.sql to a freshly restored production snapshot.
--
-- This is a point-in-time reference snapshot of the full schema, not a
-- migration and not consumed by the application (Hibernate manages the
-- live schema via hibernate.hbm2ddl.auto=update). Schema changes belong
-- in db/unapplied_updates.sql; this file will be silently overwritten on
-- the next refresh/restore run. See docs/database-release-practice.md.
-- ============================================================

