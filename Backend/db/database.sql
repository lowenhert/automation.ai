use reportingdb;
CREATE TABLE stb_inventory (
                               id INT AUTO_INCREMENT PRIMARY KEY,
                               uuid CHAR(36) NOT NULL,
                               vendor VARCHAR(50) NOT NULL,
                               model VARCHAR(50) NOT NULL,
                               resolution ENUM('HD','SD') NOT NULL,
                               stb_no INT NOT NULL,
                               UNIQUE KEY uk_uuid (uuid)
);

INSERT INTO stb_inventory (uuid, vendor, model, resolution, stb_no) VALUES
                                                                        (UUID(),'SYNAMEDIA','IVY','HD',45),
                                                                        (UUID(),'SYNAMEDIA','Phoenix-1','HD',42),
                                                                        (UUID(),'SYNAMEDIA','Phoenix-1','HD',43),
                                                                        (UUID(),'SYNAMEDIA','Phoenix-3','HD',44),
                                                                        (UUID(),'SYNAMEDIA','Samsung','HD',74),
                                                                        (UUID(),'SYNAMEDIA','Samsung','HD',75),
                                                                        (UUID(),'SYNAMEDIA','Leo_PVR','HD',62),
                                                                        (UUID(),'SYNAMEDIA','NOVA','HD',86),
                                                                        (UUID(),'SYNAMEDIA','Jade','HD',77),
                                                                        (UUID(),'SYNAMEDIA','Aslan_PVR','HD',61),
                                                                        (UUID(),'SYNAMEDIA','T-51','HD',27),
                                                                        (UUID(),'SYNAMEDIA','T-52','HD',28),
                                                                        (UUID(),'SYNAMEDIA','Kaon','HD',65),
                                                                        (UUID(),'SYNAMEDIA','Miracle','HD',53),
                                                                        (UUID(),'SYNAMEDIA','Panna','HD',87),
                                                                        (UUID(),'SYNAMEDIA','Panna','HD',88),
                                                                        (UUID(),'SYNAMEDIA','Jupiter','HD',84),
                                                                        (UUID(),'SYNAMEDIA','Jupiter','HD',85),
                                                                        (UUID(),'SYNAMEDIA','T-41','SD',41),
                                                                        (UUID(),'SYNAMEDIA','Sapphire','SD',13),
                                                                        (UUID(),'SYNAMEDIA','Neptune','SD',98),
                                                                        (UUID(),'SYNAMEDIA','Mars-1','SD',94),
                                                                        (UUID(),'SYNAMEDIA','Mars-2','SD',95),
                                                                        (UUID(),'SYNAMEDIA','Heera','SD',16);


INSERT INTO stb_inventory (uuid, vendor, model, resolution, stb_no) VALUES
                                                                        (UUID(),'IRDETO','Nysa','HD',34),
                                                                        (UUID(),'IRDETO','Navya','HD',30),
                                                                        (UUID(),'IRDETO','Navya-2','HD',31),
                                                                        (UUID(),'IRDETO','Navya-2','HD',32),
                                                                        (UUID(),'IRDETO','Navya-3','HD',33),
                                                                        (UUID(),'IRDETO','Prithivi-1','HD',38),
                                                                        (UUID(),'IRDETO','Prithivi-2','HD',39),
                                                                        (UUID(),'IRDETO','Prithivi-4','HD',35);

CREATE INDEX idx_vendor_model
    ON stb_inventory(vendor, model);

CREATE INDEX idx_resolution
    ON stb_inventory(resolution);

CREATE TABLE ottautomationtestresult (
                                         id BIGINT AUTO_INCREMENT PRIMARY KEY,

                                         ott_app_name VARCHAR(100),
                                         content_name VARCHAR(255),
                                         playback_time_seconds INT,
                                         timestamp DATETIME,
                                         device_id VARCHAR(50),
                                         status VARCHAR(20),

                                         screenshots_folder TEXT,
                                         error_type VARCHAR(100),
                                         error_message TEXT,
                                         failed_step VARCHAR(100),

                                         INDEX idx_app_name (ott_app_name),
                                         INDEX idx_status (status),
                                         INDEX idx_timestamp (timestamp)
);

CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(255) NOT NULL,
  `password` varchar(255) NOT NULL,
  `email` varchar(255) DEFAULT NULL,
  `role` varchar(255) DEFAULT NULL,
  `enabled` tinyint(1) DEFAULT '1',
  `active` bit(1) NOT NULL,
  `first_name` varchar(255) DEFAULT NULL,
  `last_name` varchar(255) DEFAULT NULL,
  `phone_number` varchar(255) DEFAULT NULL,
  `email_verified` tinyint(1) DEFAULT '0',
  `email_verification_token` varchar(255) DEFAULT NULL,
  `email_verification_token_expires_at` timestamp NULL DEFAULT NULL,
  `password_reset_token` varchar(255) DEFAULT NULL,
  `password_reset_token_expires_at` timestamp NULL DEFAULT NULL,
  `provider` varchar(255) DEFAULT NULL,
  `avatar` json DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`),
  UNIQUE KEY `email` (`email`),
  KEY `FKibk1e3kaxy5sfyeekp8hbhnim` (`created_by`),
  KEY `FKci7xr690rvyv3bnfappbyh8x0` (`updated_by`),
  CONSTRAINT `FKci7xr690rvyv3bnfappbyh8x0` FOREIGN KEY (`updated_by`) REFERENCES `users` (`id`),
  CONSTRAINT `FKibk1e3kaxy5sfyeekp8hbhnim` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO users (
    id,
    username,
    password,
    email,
    role,
    enabled,
    active,
    first_name,
    last_name,
    phone_number,
    email_verified,
    email_verification_token,
    email_verification_token_expires_at,
    password_reset_token,
    password_reset_token_expires_at,
    provider,
    avatar,
    created_by,
    updated_by,
    created_at,
    updated_at
) VALUES (
    1,
    'admin',
    '$2a$10$W5yaRhvvrS3H8SLRunOqDOtjkRMx0tyTQgMRBEMJ7Crtzvm2vIH0m',
    'admin@tataplay.com',
    'ADMIN',
    1,
    b'1',
    'Aman',
    'Sharma',
    '9084368991',
    0,
    NULL,
    NULL,
    NULL,
    NULL,
    'local',
    NULL,
    NULL,
    NULL,
    '2025-10-20 18:37:26',
    '2026-01-21 11:07:36'
);