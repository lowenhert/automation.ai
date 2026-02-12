-- MySQL dump 10.13  Distrib 8.0.40, for Win64 (x86_64)
--
-- Host: localhost    Database: patchdb
-- ------------------------------------------------------
-- Server version	8.0.40

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `patch_criticality`
--

DROP TABLE IF EXISTS `patch_criticality`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `patch_criticality` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `description` varchar(255) DEFAULT NULL,
  `detected_at` datetime(6) DEFAULT NULL,
  `package_name` varchar(255) DEFAULT NULL,
  `server_id` bigint DEFAULT NULL,
  `severity` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `patch_criticality`
--

LOCK TABLES `patch_criticality` WRITE;
/*!40000 ALTER TABLE `patch_criticality` DISABLE KEYS */;
INSERT INTO `patch_criticality` VALUES (1,'Kernel privilege escalation (CVE-2025-1012)','2025-10-25 10:32:00.000000','kernel',1,'Critical'),(2,'OpenSSL buffer overflow vulnerability (CVE-2025-1209)','2025-10-27 08:12:00.000000','openssl',1,'High'),(3,'Sudo local privilege escalation','2025-10-26 13:40:00.000000','sudo',2,'Medium'),(4,'Glibc memory leak in locale handling','2025-10-29 18:20:00.000000','glibc',3,'Low'),(5,'Bash remote code execution vulnerability','2025-10-30 07:50:00.000000','bash',1,'Critical'),(6,'Systemd service DoS issue','2025-10-24 21:30:00.000000','systemd',2,'Medium'),(7,'Python3 SSL certificate validation bug','2025-10-23 15:10:00.000000','python3',3,'Low'),(8,'Gzip decompression heap overflow','2025-10-28 16:05:00.000000','gzip',1,'High'),(9,'OpenSSH authentication bypass','2025-10-29 09:45:00.000000','openssh',2,'Critical'),(10,'Libcurl information disclosure','2025-10-30 12:25:00.000000','libcurl',3,'Medium');
/*!40000 ALTER TABLE `patch_criticality` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `patch_logs`
--

DROP TABLE IF EXISTS `patch_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `patch_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `server_id` bigint NOT NULL,
  `patch_type` varchar(255) DEFAULT NULL,
  `output` text,
  `status` varchar(255) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `server_id` (`server_id`),
  CONSTRAINT `patch_logs_ibfk_1` FOREIGN KEY (`server_id`) REFERENCES `server` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `patch_logs`
--

LOCK TABLES `patch_logs` WRITE;
/*!40000 ALTER TABLE `patch_logs` DISABLE KEYS */;
INSERT INTO `patch_logs` VALUES (1,1,'prepatch','---\nstart_time: 2025-10-28T09:00:00Z\nserver: rhel-server-01\nactions:\n  - Check disk space: OK\n  - Verify network: OK\n  - Stop critical services: Completed\nresult: SUCCESS\nend_time: 2025-10-28T09:10:00Z','Success','2025-10-28 03:40:00'),(2,1,'patch','---\nstart_time: 2025-10-28T09:15:00Z\npackages_updated:\n  - kernel-5.14.0-391.el9\n  - openssl-3.0.7-2.el9\n  - glibc-2.34-60.el9\nstatus: All updates applied\nresult: SUCCESS\nend_time: 2025-10-28T09:45:00Z','Success','2025-10-28 04:15:00'),(3,1,'postpatch','---\nstart_time: 2025-10-28T09:50:00Z\nserver_reboot: SUCCESS\nservice_check:\n  - httpd: Running\n  - sshd: Running\nvalidation: All OK\nresult: SUCCESS\nend_time: 2025-10-28T10:00:00Z','Success','2025-10-28 04:30:00'),(4,2,'prepatch','---\nstart_time: 2025-10-29T07:00:00Z\nserver: rhel-server-02\nactions:\n  - Verify backup: OK\n  - Check yum repo availability: OK\nresult: SUCCESS\nend_time: 2025-10-29T07:10:00Z','Success','2025-10-29 01:40:00'),(5,2,'patch','---\nstart_time: 2025-10-29T07:15:00Z\npackages_updated:\n  - systemd-252.3-5.el9\n  - bash-5.1.8-6.el9\n  - sudo-1.9.12-1.el9\nstatus: Partial update completed\nerrors:\n  - sudo update required reboot\nresult: PARTIAL\nend_time: 2025-10-29T07:45:00Z','Partial','2025-10-29 02:15:00'),(6,2,'postpatch','---\nstart_time: 2025-10-29T07:50:00Z\nserver_reboot: SUCCESS\nvalidation:\n  - sudo version check: OK\n  - systemd logs clean: OK\nresult: SUCCESS\nend_time: 2025-10-29T08:00:00Z','Success','2025-10-29 02:30:00'),(7,3,'prepatch','---\nstart_time: 2025-10-30T06:00:00Z\nserver: rhel-server-03\nactions:\n  - Snapshot VM: Completed\n  - Stop non-critical services: Done\nresult: SUCCESS\nend_time: 2025-10-30T06:15:00Z','Success','2025-10-30 00:45:00'),(8,3,'patch','---\nstart_time: 2025-10-30T06:20:00Z\npackages_updated:\n  - python3-3.9.18-1.el9\n  - curl-7.85.0-6.el9\n  - gzip-1.12-4.el9\nstatus: All updates installed\nresult: SUCCESS\nend_time: 2025-10-30T06:50:00Z','Success','2025-10-30 01:20:00'),(9,3,'postpatch','---\nstart_time: 2025-10-30T06:55:00Z\nvalidation:\n  - systemctl check: OK\n  - application restart: OK\nresult: SUCCESS\nend_time: 2025-10-30T07:05:00Z','Success','2025-10-30 01:35:00'),(10,1,'patch','---\nstart_time: 2025-10-31T01:00:00Z\npackages_updated:\n  - openssh-9.2p1-3.el9\n  - libcurl-8.0.1-1.el9\nerrors: None\nresult: SUCCESS\nend_time: 2025-10-31T01:20:00Z','Success','2025-10-30 19:50:00'),(11,2,'patch','---\nstart_time: 2025-10-31T02:10:00Z\npackages_updated:\n  - openssl-3.0.8-2.el9\n  - kernel-5.14.0-393.el9\nwarnings: Requires reboot\nresult: PARTIAL\nend_time: 2025-10-31T02:40:00Z','Partial','2025-10-30 21:10:00'),(12,3,'patch','---\nstart_time: 2025-10-31T03:00:00Z\npackages_updated:\n  - glibc-2.34-70.el9\n  - systemd-252.5-1.el9\n  - python3-3.9.19-1.el9\nresult: SUCCESS\nend_time: 2025-10-31T03:25:00Z','Success','2025-10-30 21:55:00'),(13,1,'postpatch','---\nstart_time: 2025-10-31T01:25:00Z\nreboot: Completed\nservice_check:\n  - sshd: Running\n  - httpd: Running\nvalidation: All OK\nresult: SUCCESS\nend_time: 2025-10-31T01:35:00Z','Success','2025-10-30 20:05:00'),(14,2,'postpatch','---\nstart_time: 2025-10-31T02:45:00Z\nreboot: Completed\nvalidation:\n  - kernel version: 5.14.0-393.el9\n  - openssl version: OK\nresult: SUCCESS\nend_time: 2025-10-31T03:00:00Z','Success','2025-10-30 21:30:00'),(15,3,'postpatch','---\nstart_time: 2025-10-31T03:30:00Z\nservice_check:\n  - nginx: Running\n  - sshd: Running\nvalidation: PASS\nresult: SUCCESS\nend_time: 2025-10-31T03:40:00Z','Success','2025-10-30 22:10:00'),(16,1,'prepatch','---\nstart_time: 2025-10-27T21:00:00Z\nbackup_status: SUCCESS\nchecks:\n  - Network: OK\n  - Disk usage: 72%\nresult: SUCCESS\nend_time: 2025-10-27T21:10:00Z','Success','2025-10-27 15:40:00'),(17,2,'prepatch','---\nstart_time: 2025-10-27T21:15:00Z\nsnapshot_created: TRUE\npackage_repo_check: OK\nresult: SUCCESS\nend_time: 2025-10-27T21:25:00Z','Success','2025-10-27 15:55:00'),(18,3,'prepatch','---\nstart_time: 2025-10-27T21:30:00Z\nserver: rhel-03\nvalidation:\n  - Disk clean: OK\n  - YUM cache: cleared\nresult: SUCCESS\nend_time: 2025-10-27T21:40:00Z','Success','2025-10-27 16:10:00'),(19,1,'patch','---\nstart_time: 2025-10-31T06:00:00Z\npackages_updated:\n  - sudo-1.9.13-1.el9\n  - curl-7.87.0-5.el9\nstatus: SUCCESS\nresult: SUCCESS\nend_time: 2025-10-31T06:25:00Z','Success','2025-10-31 00:55:00'),(20,2,'patch','---\nstart_time: 2025-10-31T06:30:00Z\npackages_updated:\n  - bash-5.2.15-1.el9\n  - gzip-1.13-2.el9\nresult: SUCCESS\nend_time: 2025-10-31T06:50:00Z','Success','2025-10-31 01:20:00'),(21,1,'patch','PLAY [Centralized Linux Patch Management System]\r \r TASK [Gathering Facts]\r ok: [rhel-server]\r ok: [ubuntu-server]\r ok: [centos-server]\r \r TASK [Display system information for logging]\r ok: [rhel-server] => {\"msg\": \"Starting patch process for rhel-server - RedHat 8.7\"}\r ok: [ubuntu-server] => {\"msg\": \"Starting patch process for ubuntu-server - Ubuntu 20.04\"}\r ok: [centos-server] => {\"msg\": \"Starting patch process for centos-server - CentOS 9.2\"}\r \r TASK [Log patch job start in MySQL]\r changed: [rhel-server]\r changed: [ubuntu-server]\r changed: [centos-server]\r \r TASK [Check for available updates on RedHat/CentOS systems]\r skipping: [ubuntu-server]\r included: /etc/ansible/roles/patch/tasks/redhat-updates.yml for rhel-server, centos-server\r \r TASK [Check security updates]\r ok: [rhel-server]\r ok: [centos-server]\r \r TASK [Log available security updates]\r ok: [rhel-server] => {\"msg\": \"Found 3 security updates on rhel-server\"}\r ok: [centos-server] => {\"msg\": \"Found 2 security updates on centos-server\"}\r \r TASK [Check for available updates on Ubuntu/Debian systems]\r skipping: [rhel-server]\r skipping: [centos-server]\r included: /etc/ansible/roles/patch/tasks/ubuntu-updates.yml for ubuntu-server\r \r TASK [Update package cache]\r changed: [ubuntu-server]\r \r TASK [Check upgradable packages]\r ok: [ubuntu-server]\r \r TASK [Log available updates count]\r ok: [ubuntu-server] => {\"msg\": \"Package cache updated and updates checked on ubuntu-server\"}\r \r TASK [Simulate package updates (Dry Run)]\r included: /etc/ansible/roles/patch/tasks/simulation.yml for rhel-server, ubuntu-server, centos-server\r \r TASK [Simulate updates on RedHat/CentOS]\r changed: [rhel-server]\r changed: [centos-server]\r \r TASK [Simulate updates on Ubuntu]\r changed: [ubuntu-server]\r \r TASK [Log patch simulation results to MySQL]\r changed: [rhel-server]\r changed: [ubuntu-server]\r changed: [centos-server]\r \r TASK [Generate packages list for logging]\r ok: [rhel-server]\r ok: [ubuntu-server]\r ok: [centos-server]\r \r TASK [Log detailed update information to MySQL]\r changed: [rhel-server]\r changed: [ubuntu-server]\r changed: [centos-server]\r \r TASK [Display patch execution summary]\r ok: [rhel-server] => {\"msg\": \"PATCH SUMMARY - rhel-server: 3 security updates available. Dry run completed successfully. Logs stored in MySQL.\"}\r ok: [ubuntu-server] => {\"msg\": \"PATCH SUMMARY - ubuntu-server: 5 packages can be upgraded. Dry run completed successfully. Logs stored in MySQL.\"}\r ok: [centos-server] => {\"msg\": \"PATCH SUMMARY - centos-server: 2 security updates available. Dry run completed successfully. Logs stored in MySQL.\"}\r \r TASK [Log final completion status]\r changed: [rhel-server]\r changed: [ubuntu-server]\r changed: [centos-server]\r \r PLAY RECAP\r rhel-server: ok=12 changed=6 unreachable=0 failed=0 skipped=2 rescued=0 ignored=0\r ubuntu-server: ok=11 changed=5 unreachable=0 failed=0 skipped=3 rescued=0 ignored=0\r centos-server: ok=12 changed=6 unreachable=0 failed=0 skipped=2 rescued=0 ignored=0\r \r EXECUTION COMPLETED SUCCESSFULLY\r Total hosts: 3 | Successful: 3 | Failed: 0\r Logs stored in MySQL database: patch_db@192.168.10.20','Success','2025-10-28 04:15:00');
/*!40000 ALTER TABLE `patch_logs` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `patch_schedule`
--

DROP TABLE IF EXISTS `patch_schedule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `patch_schedule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `server_id` bigint DEFAULT NULL,
  `schedule_type` varchar(20) DEFAULT NULL,
  `scheduled_time` datetime DEFAULT NULL,
  `enabled` tinyint(1) DEFAULT '1',
  `awx_schedule_id` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(255) DEFAULT NULL,
  `scheduled_at` datetime(6) DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  `template_name` varchar(255) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `server_id` (`server_id`),
  CONSTRAINT `patch_schedule_ibfk_1` FOREIGN KEY (`server_id`) REFERENCES `server` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `patch_schedule`
--

LOCK TABLES `patch_schedule` WRITE;
/*!40000 ALTER TABLE `patch_schedule` DISABLE KEYS */;
INSERT INTO `patch_schedule` VALUES (1,1,NULL,NULL,1,NULL,'2025-11-01 09:41:37.005846',NULL,'2026-01-15 14:30:00.000000','SCHEDULED','PATCH_TEMPLATE','2025-11-01 09:41:37.005846');
/*!40000 ALTER TABLE `patch_schedule` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `precheck_result`
--

DROP TABLE IF EXISTS `precheck_result`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `precheck_result` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `server_id` bigint DEFAULT NULL,
  `disk_usage` varchar(255) DEFAULT NULL,
  `memory_status` varchar(255) DEFAULT NULL,
  `uptime` varchar(255) DEFAULT NULL,
  `package_updates` varchar(255) DEFAULT NULL,
  `timestamp` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `server_id` (`server_id`),
  CONSTRAINT `precheck_result_ibfk_1` FOREIGN KEY (`server_id`) REFERENCES `server` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `precheck_result`
--

LOCK TABLES `precheck_result` WRITE;
/*!40000 ALTER TABLE `precheck_result` DISABLE KEYS */;
/*!40000 ALTER TABLE `precheck_result` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `script_executions`
--

DROP TABLE IF EXISTS `script_executions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `script_executions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `script_id` bigint NOT NULL,
  `status` enum('RUNNING','COMPLETED','FAILED','CANCELLED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `output` longtext COLLATE utf8mb4_unicode_ci,
  `error` tinytext COLLATE utf8mb4_unicode_ci,
  `executed_at` datetime DEFAULT NULL,
  `duration_ms` bigint DEFAULT NULL,
  `started_at` datetime DEFAULT NULL,
  `finished_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `script_id` (`script_id`),
  CONSTRAINT `script_executions_ibfk_1` FOREIGN KEY (`script_id`) REFERENCES `scripts` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=47 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `script_executions`
--

LOCK TABLES `script_executions` WRITE;
/*!40000 ALTER TABLE `script_executions` DISABLE KEYS */;
INSERT INTO `script_executions` VALUES (1,2,'FAILED',NULL,'Execution error: Cannot invoke \"com.lowenhert.patchmanagement.core.script.util.ScriptExecutor.execute(String, java.util.function.Consumer)\" because the return value of \"java.util.Map.get(Object)\" is null','2025-08-10 15:51:34',NULL,NULL,NULL),(2,2,'FAILED',NULL,'Execution error: Cannot invoke \"com.lowenhert.patchmanagement.core.script.util.ScriptExecutor.execute(String, java.util.function.Consumer)\" because the return value of \"java.util.Map.get(Object)\" is null','2025-08-10 15:52:54',NULL,NULL,NULL),(3,2,'FAILED',NULL,'Execution error: Cannot invoke \"com.lowenhert.patchmanagement.core.script.util.ScriptExecutor.execute(String, java.util.function.Consumer)\" because the return value of \"java.util.Map.get(Object)\" is null','2025-08-10 15:53:18',NULL,NULL,NULL),(4,2,'FAILED',NULL,'Execution error: Cannot invoke \"com.lowenhert.patchmanagement.core.script.util.ScriptExecutor.execute(String, java.util.function.Consumer)\" because the return value of \"java.util.Map.get(Object)\" is null','2025-08-10 15:54:12',NULL,NULL,NULL),(5,2,'FAILED',NULL,'Execution error: Cannot invoke \"com.lowenhert.patchmanagement.core.script.util.ScriptExecutor.execute(String, java.util.function.Consumer)\" because the return value of \"java.util.Map.get(Object)\" is null','2025-08-10 15:54:43',NULL,NULL,NULL),(6,2,'FAILED',NULL,'Execution error: Cannot invoke \"com.lowenhert.patchmanagement.core.script.util.ScriptExecutor.execute(String, java.util.function.Consumer)\" because the return value of \"java.util.Map.get(Object)\" is null','2025-08-10 15:54:58',NULL,NULL,NULL),(7,2,'FAILED',NULL,'Execution error: Cannot invoke \"com.lowenhert.patchmanagement.core.script.util.ScriptExecutor.execute(String, java.util.function.Consumer)\" because the return value of \"java.util.Map.get(Object)\" is null','2025-08-10 15:55:15',NULL,NULL,NULL),(8,2,'FAILED',NULL,'Execution error: Cannot invoke \"com.lowenhert.patchmanagement.core.script.util.ScriptExecutor.execute(String, java.util.function.Consumer)\" because the return value of \"java.util.Map.get(Object)\" is null','2025-08-10 16:02:29',NULL,NULL,NULL),(9,2,'FAILED','Python was not found; run without arguments to install from the Microsoft Store, or disable this shortcut from Settings > Apps > Advanced app settings > App execution aliases.\n',NULL,'2025-08-10 16:03:25',180,NULL,'2025-08-10 16:03:26'),(10,2,'FAILED','Python was not found; run without arguments to install from the Microsoft Store, or disable this shortcut from Settings > Apps > Advanced app settings > App execution aliases.\n',NULL,'2025-08-10 16:06:28',147,NULL,'2025-08-10 16:06:28'),(11,2,'FAILED','Python was not found; run without arguments to install from the Microsoft Store, or disable this shortcut from Settings > Apps > Advanced app settings > App execution aliases.\n',NULL,'2025-08-10 16:17:26',119,NULL,'2025-08-10 16:17:26'),(12,2,'FAILED','Python was not found; run without arguments to install from the Microsoft Store, or disable this shortcut from Settings > Apps > Advanced app settings > App execution aliases.\n',NULL,'2025-08-10 16:17:51',108,NULL,'2025-08-10 16:17:52'),(13,2,'FAILED','Traceback (most recent call last):\n  File \"C:\\Users\\sharm\\AppData\\Local\\Temp\\python-8362561122287441600.py\", line 2, in <module>\n    import psutil\nModuleNotFoundError: No module named \'psutil\'\n',NULL,'2025-08-10 16:18:39',113,NULL,'2025-08-10 16:18:39'),(14,2,'COMPLETED','System: Windows\nCPU Usage: 7.3%\nMemory Available: 3.66 GB\n',NULL,'2025-08-10 16:33:30',1158,NULL,'2025-08-10 16:33:31'),(18,2,'COMPLETED','Starting long task...\nStep 1/10: working...\nStep 2/10: working...\nStep 3/10: working...\nStep 4/10: working...\nStep 5/10: working...\nStep 6/10: working...\nStep 7/10: working...\nStep 8/10: working...\nStep 9/10: working...\nStep 10/10: working...\nTask completed successfully!\n',NULL,'2025-08-10 16:41:18',30073,NULL,'2025-08-10 16:41:48'),(19,2,'COMPLETED',NULL,NULL,'2025-08-10 17:07:28',NULL,NULL,NULL),(20,2,'COMPLETED',NULL,NULL,'2025-08-10 17:16:19',NULL,NULL,NULL),(21,2,'COMPLETED',NULL,NULL,'2025-08-13 12:40:11',NULL,NULL,NULL),(22,2,'COMPLETED',NULL,NULL,'2025-08-13 12:41:29',NULL,NULL,NULL),(23,2,'COMPLETED',NULL,NULL,'2025-08-13 12:47:23',NULL,NULL,NULL),(24,2,'COMPLETED',NULL,NULL,'2025-08-13 14:30:11',NULL,NULL,NULL),(25,2,'COMPLETED',NULL,NULL,'2025-08-13 14:31:43',NULL,NULL,NULL),(26,2,'COMPLETED',NULL,NULL,'2025-08-13 14:36:09',NULL,NULL,NULL),(27,2,'COMPLETED',NULL,NULL,'2025-08-13 14:38:31',NULL,NULL,NULL),(28,3,'COMPLETED',NULL,NULL,'2025-08-13 14:58:12',NULL,NULL,NULL),(33,2,'COMPLETED',NULL,NULL,'2025-10-17 21:04:51',NULL,NULL,NULL),(35,3,'COMPLETED',NULL,NULL,'2025-10-26 11:16:05',NULL,NULL,NULL),(36,4,'COMPLETED',NULL,NULL,'2025-10-26 11:16:29',NULL,NULL,NULL),(37,5,'COMPLETED',NULL,NULL,'2025-10-26 11:18:52',NULL,NULL,NULL),(38,5,'COMPLETED',NULL,NULL,'2025-10-26 11:18:55',NULL,NULL,NULL),(39,5,'COMPLETED',NULL,NULL,'2025-10-26 11:19:10',NULL,NULL,NULL),(40,6,'COMPLETED',NULL,NULL,'2025-10-26 11:25:53',NULL,NULL,NULL),(41,6,'COMPLETED',NULL,NULL,'2025-10-26 11:25:56',NULL,NULL,NULL),(42,6,'COMPLETED',NULL,NULL,'2025-10-26 11:25:57',NULL,NULL,NULL),(43,6,'COMPLETED',NULL,NULL,'2025-10-26 11:26:15',NULL,NULL,NULL),(45,3,'COMPLETED',NULL,NULL,'2025-10-26 11:29:36',NULL,NULL,NULL),(46,2,'COMPLETED',NULL,NULL,'2025-10-26 11:30:02',NULL,NULL,NULL);
/*!40000 ALTER TABLE `script_executions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `scripts`
--

DROP TABLE IF EXISTS `scripts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `scripts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `type` enum('ANSIBLE','POWERSHELL','SHELL','PYTHON','ANGULAR_CLI','BATCH','BASH','CUSTOM') COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` text COLLATE utf8mb4_unicode_ci,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `active` tinyint(1) DEFAULT '1',
  `created_by_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `created_by` (`created_by`),
  KEY `FK64qqidn983s0p8sqcltqetue0` (`created_by_id`),
  CONSTRAINT `FK64qqidn983s0p8sqcltqetue0` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `scripts_ibfk_1` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `scripts`
--

LOCK TABLES `scripts` WRITE;
/*!40000 ALTER TABLE `scripts` DISABLE KEYS */;
INSERT INTO `scripts` VALUES (2,'Long Wait Script','Simulates a long-running task with periodic log output','PYTHON','#!/usr/bin/env python3\nimport time\nimport sys\n\ndef long_task():\n    print(\"Starting long task...\", flush=True)\n    for i in range(1, 10 + 1):\n        print(f\"Step {i}/10: working...\", flush=True)\n        time.sleep(3)\n    print(\"Task completed successfully!\", flush=True)\n\nif __name__ == \"__main__\":\n    long_task()',NULL,'2025-08-10 15:23:42','2025-08-10 16:35:16',1,1),(3,'Dummy Ansible Script','A dummy Ansible playbook for testing execution','ANSIBLE','---\n- name: Dummy Playbook for Testing\n  hosts: localhost\n  gather_facts: no\n  tasks:\n    - name: Print Hello World\n      debug:\n        msg: \"Hello World! This is a dummy Ansible playbook.\"\n\n    - name: Create a temporary file\n      copy:\n        dest: /tmp/ansible_test_file.txt\n        content: \"This is a test file created by Ansible.\"',NULL,'2025-08-13 14:57:05','2025-08-13 14:57:05',1,1),(4,'Linux Patch Log Simulator','An Ansible playbook that runs the Python-based Linux kernel patch log simulator for demo or testing purposes.','PYTHON','---\n- name: Run Linux Patch Log Simulator\n  hosts: localhost\n  gather_facts: no\n  tasks:\n    - name: Ensure log directory exists\n      file:\n        path: /tmp/linux_patch_logs\n        state: directory\n        mode: \'0755\'\n\n    - name: Create the Python simulation script\n      copy:\n        dest: /tmp/linux_patch_log_simulator.py\n        mode: \'0755\'\n        content: |\n          #!/usr/bin/env python3\n          import logging, os, time\n          from datetime import datetime\n\n          LOG_DIR = \'/tmp/linux_patch_logs\'\n          os.makedirs(LOG_DIR, exist_ok=True)\n          TIMESTAMP = datetime.now().strftime(\'%Y%m%d_%H%M%S\')\n          LOG_FILE = os.path.join(LOG_DIR, f\'patch_simulation_{TIMESTAMP}.log\')\n\n          logging.basicConfig(\n              level=logging.INFO,\n              format=\'%(asctime)s [%(levelname)s] %(message)s\',\n              handlers=[\n                  logging.FileHandler(LOG_FILE),\n                  logging.StreamHandler()\n              ]\n          )\n\n          logger = logging.getLogger(\'PatchSimulator\')\n\n          def simulate_step(step, substeps, delay):\n              logger.info(f\'=== {step} STARTED ===\')\n              for s in substeps:\n                  logger.info(f\'[{step}] {s} ...\')\n                  time.sleep(delay)\n                  logger.info(f\'[{step}] {s} completed.\')\n              logger.info(f\'=== {step} COMPLETED ===\\n\')\n\n          logger.info(\'Linux Kernel Patch Log Simulation Started\')\n          logger.info(\'Simulated Target: 10.0.0.1 (example server)\')\n\n          simulate_step(\'Pre-Check\', [\n              \'Checking current kernel version\',\n              \'Checking disk space in /boot\',\n              \'Checking yum/dnf locks\',\n              \'Checking SELinux and NTP status\',\n              \'Validating network reachability\',\n              \'Verifying package manager configuration\'\n          ], 0.5)\n\n          simulate_step(\'Kernel Patch Process\', [\n              \'Downloading latest kernel package\',\n              \'Installing kernel and dependencies\',\n              \'Updating grub configuration\',\n              \'Cleaning up old package cache\'\n          ], 0.7)\n\n          simulate_step(\'Post-Check\', [\n              \'Verifying kernel installation\',\n              \'Checking grub entries\',\n              \'Ensuring no pending reboots required\',\n              \'Summarizing final status\'\n          ], 0.5)\n\n          logger.info(\'Simulation completed successfully.\')\n          logger.info(f\'Logs saved to: {LOG_FILE}\')\n\n    - name: Run the Python patch simulation\n      command: python3 /tmp/linux_patch_log_simulator.py\n      register: simulation_output\n\n    - name: Display simulation output\n      debug:\n        var: simulation_output.stdout',NULL,'2025-10-26 11:15:34','2025-10-26 11:15:34',1,1),(5,'Linux Patch','An Ansible playbook that runs the Python-based Linux kernel patch log simulator for demo or testing purposes.','PYTHON','---\n- name: Run Linux Patch Log Simulator\n  hosts: localhost\n  gather_facts: no\n  tasks:\n    - name: Ensure log directory exists\n      file:\n        path: /tmp/linux_patch_logs\n        state: directory\n        mode: \'0755\'\n\n    - name: Create the Python simulation script\n      copy:\n        dest: /tmp/linux_patch_log_simulator.py\n        mode: \'0755\'\n        content: |\n          #!/usr/bin/env python3\n          import logging, os, time\n          from datetime import datetime\n\n          LOG_DIR = \'/tmp/linux_patch_logs\'\n          os.makedirs(LOG_DIR, exist_ok=True)\n          TIMESTAMP = datetime.now().strftime(\'%Y%m%d_%H%M%S\')\n          LOG_FILE = os.path.join(LOG_DIR, f\'patch_simulation_{TIMESTAMP}.log\')\n\n          logging.basicConfig(\n              level=logging.INFO,\n              format=\'%(asctime)s [%(levelname)s] %(message)s\',\n              handlers=[\n                  logging.FileHandler(LOG_FILE),\n                  logging.StreamHandler()\n              ]\n          )\n\n          logger = logging.getLogger(\'PatchSimulator\')\n\n          def simulate_step(step, substeps, delay):\n              logger.info(f\'=== {step} STARTED ===\')\n              for s in substeps:\n                  logger.info(f\'[{step}] {s} ...\')\n                  time.sleep(delay)\n                  logger.info(f\'[{step}] {s} completed.\')\n              logger.info(f\'=== {step} COMPLETED ===\\n\')\n\n          logger.info(\'Linux Kernel Patch Log Simulation Started\')\n          logger.info(\'Simulated Target: 10.0.0.1 (example server)\')\n\n          simulate_step(\'Pre-Check\', [\n              \'Checking current kernel version\',\n              \'Checking disk space in /boot\',\n              \'Checking yum/dnf locks\',\n              \'Checking SELinux and NTP status\',\n              \'Validating network reachability\',\n              \'Verifying package manager configuration\'\n          ], 0.5)\n\n          simulate_step(\'Kernel Patch Process\', [\n              \'Downloading latest kernel package\',\n              \'Installing kernel and dependencies\',\n              \'Updating grub configuration\',\n              \'Cleaning up old package cache\'\n          ], 0.7)\n\n          simulate_step(\'Post-Check\', [\n              \'Verifying kernel installation\',\n              \'Checking grub entries\',\n              \'Ensuring no pending reboots required\',\n              \'Summarizing final status\'\n          ], 0.5)\n\n          logger.info(\'Simulation completed successfully.\')\n          logger.info(f\'Logs saved to: {LOG_FILE}\')\n\n    - name: Run the Python patch simulation\n      command: python3 /tmp/linux_patch_log_simulator.py\n      register: simulation_output\n\n    - name: Display simulation output\n      debug:\n        var: simulation_output.stdout',NULL,'2025-10-26 11:18:46','2025-10-26 11:18:46',1,1),(6,'Linux Patching','An Ansible playbook that runs the Python-based Linux kernel patch log simulator with realistic log output for demo/testing.','PYTHON','---\n- name: Run Linux Patch Log Simulator\n  hosts: localhost\n  gather_facts: no\n  tasks:\n    - name: Create the Python simulation script\n      copy:\n        dest: /tmp/linux_patch_log_simulator.py\n        mode: \'0755\'\n        content: |\n          #!/usr/bin/env python3\n          import time\n          from datetime import datetime\n\n          def log(msg, level=\"INFO\"):\n              print(f\"[{datetime.now().strftime(\'%Y-%m-%d %H:%M:%S\')}] [{level}] {msg}\")\n\n          def run_dummy_patch():\n              log(\"===== Linux Patch Execution Started =====\", \"STARTUP\")\n              time.sleep(2)\n              log(\"Loading configuration and validating patch metadata...\")\n              time.sleep(5)\n              log(\"Connecting to patch repository server... [OK]\")\n              time.sleep(3)\n              log(\"Checking system resources (CPU, Memory, Disk)... [OK]\")\n              time.sleep(4)\n              log(\"Taking pre-patch backup snapshot...\")\n              for i in range(1, 6):\n                  time.sleep(3)\n                  log(f\"Backup progress: {i*20}%\")\n              log(\"Backup completed successfully.\")\n              time.sleep(2)\n              log(\"Verifying patch compatibility with current kernel version...\")\n              time.sleep(5)\n              log(\"Compatibility check passed for kernel 5.14.0-427.el9.x86_64.\")\n              log(\"Downloading patch package: KB-2025-LNX-SYSCORE\")\n              for i in range(0, 101, 10):\n                  time.sleep(2)\n                  log(f\"Download progress: {i}%\")\n              log(\"Download completed successfully.\")\n              log(\"Extracting and staging patch files...\")\n              time.sleep(8)\n              log(\"Files staged under /var/tmp/patch_stage/ [OK]\")\n              log(\"Applying patch updates to target components...\")\n              for i in range(0, 101, 25):\n                  time.sleep(10)\n                  log(f\"Patching progress: {i}%\")\n              log(\"Core libraries updated successfully.\")\n              log(\"Verifying system integrity and dependencies...\")\n              time.sleep(10)\n              log(\"No dependency conflicts detected.\")\n              log(\"Restarting necessary system services...\")\n              time.sleep(8)\n              restarted = [\"sshd\", \"systemd-journald\", \"crond\", \"dbus\", \"rsyslog\"]\n              for svc in restarted:\n                  time.sleep(2)\n                  log(f\"Service \'{svc}\' restarted successfully.\")\n              log(\"Performing post-patch validation checks...\")\n              time.sleep(10)\n              log(\"Checksum and version verification passed.\")\n              log(\"Updating patch status in CMDB...\")\n              time.sleep(5)\n              log(\"Patch record updated successfully for host: epc01.wipro.local\")\n              log(\"Clearing temporary patch files from staging directory...\")\n              time.sleep(5)\n              log(\"All cleanup tasks completed successfully.\")\n              log(\"===== Patch Execution Completed Successfully =====\", \"SUCCESS\")\n\n          if __name__ == \"__main__\":\n              run_dummy_patch()\n\n    - name: Run the Python patch simulation\n      command: python3 /tmp/linux_patch_log_simulator.py\n      register: simulation_output\n\n    - name: Display simulation output\n      debug:\n        var: simulation_output.stdout',NULL,'2025-10-26 11:25:39','2025-10-26 11:25:39',1,1);
/*!40000 ALTER TABLE `scripts` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `server`
--

DROP TABLE IF EXISTS `server`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `server` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `hostname` varchar(255) DEFAULT NULL,
  `ip` varchar(255) DEFAULT NULL,
  `os_type` varchar(255) DEFAULT NULL,
  `ssh_user` varchar(255) DEFAULT NULL,
  `ssh_port` int DEFAULT '22',
  `ansible_group` varchar(255) DEFAULT NULL,
  `added_on` datetime DEFAULT NULL,
  `active` tinyint(1) DEFAULT '1',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `server`
--

LOCK TABLES `server` WRITE;
/*!40000 ALTER TABLE `server` DISABLE KEYS */;
INSERT INTO `server` VALUES (1,'db-01','192.168.1.101','Ubuntu','ubuntu',22,'linux','2025-08-02 12:06:59',1),(2,'web-server-01','192.168.1.100','Linux','admin',22,'web-servers','2025-08-10 14:30:20',1),(3,'web-server-01','192.168.1.102','Linux','admin',22,'web-servers','2025-08-10 14:33:20',1),(4,'app-server-01','192.168.1.103','RHEL','ec2-user',22,'app-servers','2025-08-11 09:10:00',1),(5,'backup-server','192.168.1.104','CentOS','root',22,'infra','2025-08-12 10:15:45',1),(6,'db-02','192.168.1.105','Ubuntu','ubuntu',22,'database','2025-08-14 13:40:00',1),(7,'rhel-node-01','192.168.1.106','RHEL','ansible',22,'linux','2025-08-15 08:20:30',1),(8,'rhel-node-02','192.168.1.107','RHEL','ansible',22,'linux','2025-08-16 09:00:00',1),(9,'ubuntu-node-01','192.168.1.108','Ubuntu','ubuntu',22,'linux','2025-08-17 07:55:00',1),(10,'centos-node-01','192.168.1.109','CentOS','centos',22,'linux','2025-08-18 12:10:15',1),(11,'monitoring-server','192.168.1.110','RHEL','monitor',22,'infra','2025-08-19 11:05:00',1),(12,'file-server','192.168.1.111','Ubuntu','ubuntu',22,'storage','2025-08-20 14:30:30',1),(13,'git-server','192.168.1.112','RHEL','git',22,'devops','2025-08-21 10:50:00',1),(14,'jenkins-server','192.168.1.113','Ubuntu','jenkins',22,'devops','2025-08-22 08:45:20',1),(15,'proxy-server','192.168.1.114','CentOS','root',22,'network','2025-08-23 15:12:40',1),(16,'dns-server','192.168.1.115','RHEL','admin',22,'infra','2025-08-24 17:10:00',1),(17,'test-server-01','192.168.1.116','Ubuntu','tester',22,'qa','2025-08-25 10:05:00',1),(18,'test-server-02','192.168.1.117','Ubuntu','tester',22,'qa','2025-08-25 11:15:00',1),(19,'staging-app-01','192.168.1.118','RHEL','deploy',22,'staging','2025-08-26 09:45:30',1),(20,'loadbalancer-01','192.168.1.119','CentOS','root',22,'network','2025-08-27 13:30:00',1);
/*!40000 ALTER TABLE `server` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES (1,'admin','$2a$10$W5yaRhvvrS3H8SLRunOqDOtjkRMx0tyTQgMRBEMJ7Crtzvm2vIH0m','admin@example.com','ADMIN',1,_binary '','Aman','Sharma','9084368991',0,NULL,NULL,NULL,NULL,'local',NULL,NULL,NULL,'2025-10-20 13:07:26','2025-10-20 13:30:38');
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2025-11-08 15:32:49
