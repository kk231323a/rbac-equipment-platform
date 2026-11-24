-- 设备借用与权限管理平台：空库初始化（MySQL 8.0.43）
-- 表结构来自 2026-11-19 的实际业务库；不包含压测表或历史业务数据。
-- 先由调用方选择一个空数据库，再执行本文件；本文件不创建或切换数据库。
-- 仅执行一次。不要用于已有数据的库，也不要用 --force 忽略错误继续导入。
-- DDL 会隐式提交；下方事务只覆盖种子数据，不能回滚已经创建的表。
-- 演示密码均为 TestPassword-123，使用分别生成的 BCrypt 哈希，不是生产凭据。

SET NAMES utf8mb4;

CREATE TABLE `equipment` (
  `id` int NOT NULL AUTO_INCREMENT,
  `device_no` varchar(32) NOT NULL,
  `name` varchar(50) NOT NULL,
  `model` varchar(30) DEFAULT NULL,
  `status` char(10) NOT NULL DEFAULT 'available',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `device_no` (`device_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_user` (
  `id` int NOT NULL AUTO_INCREMENT,
  `account` varchar(50) NOT NULL,
  `name` varchar(50) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `enable` tinyint(1) NOT NULL DEFAULT '1',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `account` (`account`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_role` (
  `id` int NOT NULL AUTO_INCREMENT,
  `role_code` varchar(30) NOT NULL,
  `role_name` varchar(20) NOT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_permission` (
  `id` int NOT NULL AUTO_INCREMENT,
  `permission_code` varchar(64) NOT NULL,
  `permission_name` char(20) NOT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `permission_code` (`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_user_role` (
  `id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `role_id` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`,`role_id`),
  KEY `fk_user_role_role` (`role_id`),
  CONSTRAINT `fk_user_role_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`),
  CONSTRAINT `fk_user_role_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户具有的角色表';

CREATE TABLE `sys_role_permission` (
  `id` int NOT NULL AUTO_INCREMENT,
  `role_id` int NOT NULL,
  `permission_id` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`,`permission_id`),
  KEY `fk_role_permission_permission` (`permission_id`),
  CONSTRAINT `fk_role_permission_permission` FOREIGN KEY (`permission_id`) REFERENCES `sys_permission` (`id`),
  CONSTRAINT `fk_role_permission_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `borrow_request` (
  `id` int NOT NULL AUTO_INCREMENT,
  `device_id` int NOT NULL,
  `borrower_id` int NOT NULL,
  `approver_id` int DEFAULT NULL,
  `status` char(10) NOT NULL DEFAULT 'pending',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `approve_time` timestamp NULL DEFAULT NULL,
  `due_time` timestamp NULL DEFAULT NULL,
  `borrow_time` timestamp NULL DEFAULT NULL,
  `return_time` timestamp NULL DEFAULT NULL,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_borrow_device` (`device_id`),
  KEY `fk_borrow_borrower` (`borrower_id`),
  KEY `fk_borrow_approver` (`approver_id`),
  CONSTRAINT `fk_borrow_approver` FOREIGN KEY (`approver_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_borrow_borrower` FOREIGN KEY (`borrower_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_borrow_device` FOREIGN KEY (`device_id`) REFERENCES `equipment` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `operator_id` int DEFAULT NULL COMMENT '已认证操作人ID，系统或匿名为空',
  `operator_type` varchar(16) NOT NULL DEFAULT 'user' COMMENT 'user/system/anonymous',
  `operator_account` varchar(64) DEFAULT NULL COMMENT '登录尝试账号，不保存密码',
  `action_code` varchar(64) NOT NULL COMMENT '操作标识',
  `resource_type` varchar(32) NOT NULL COMMENT '资源类型',
  `resource_id` int DEFAULT NULL COMMENT '资源ID，尚未创建或非法ID为空',
  `result` varchar(16) NOT NULL COMMENT '操作结果',
  `error_code` int DEFAULT NULL,
  `error_message` varchar(255) DEFAULT NULL,
  `request_path` varchar(255) DEFAULT NULL COMMENT '接口模板，不含请求体、查询串或Token',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `details` json DEFAULT NULL COMMENT '操作明细',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `auth_state_sync_task` (
  `user_id` INT NOT NULL,
  `target_token_version` BIGINT NOT NULL,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

START TRANSACTION;

INSERT INTO sys_role (role_code, role_name) VALUES
  ('borrower', '借用人'),
  ('admin', '管理员');

INSERT INTO sys_permission (permission_code, permission_name) VALUES
  ('equipment:read', '设备查询'),
  ('borrow:create', '提交借用申请'),
  ('borrow:approved', '批准借用申请'),
  ('borrow:return', '确认归还'),
  ('equipment:create', '新增设备'),
  ('equipment:update', '修改设备'),
  ('audit:read', '查看审计日志'),
  ('user:enable', '启用用户'),
  ('user:disable', '禁用用户'),
  ('user:read', '查看用户'),
  ('user:create', '创建用户'),
  ('user:role:assign', '分配用户角色'),
  ('user:role:revoke', '撤销用户角色'),
  ('role:permission:assign', '授予角色权限'),
  ('role:permission:revoke', '撤销角色权限');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE (r.role_code = 'borrower' AND p.permission_code IN (
         'equipment:read', 'borrow:create'
      ))
   OR (r.role_code = 'admin' AND p.permission_code IN (
         'equipment:read', 'equipment:create', 'equipment:update',
         'borrow:approved', 'borrow:return', 'audit:read',
          'user:enable', 'user:disable', 'user:read', 'user:create',
         'user:role:assign', 'user:role:revoke',
         'role:permission:assign', 'role:permission:revoke'
      ));

INSERT INTO sys_user (account, name, password_hash, enable) VALUES
  ('schema_test_01', '演示借用人', '$2a$10$22M9gJRglRF9GYuqJLH02efcnvZkNMXwpW51P/ugPye9HdoXgvQq2', 1),
  ('schema_admin_01', '演示管理员', '$2a$10$3tH0Oumo4kgc3PHRBOx1Fu.Xzw6WAtbMSdqdnPFqQ4aHni7ydedN2', 1);

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r
  ON (u.account = 'schema_test_01' AND r.role_code = 'borrower')
  OR (u.account = 'schema_admin_01' AND r.role_code = 'admin');

INSERT INTO equipment (device_no, name, model, status) VALUES
  ('TEST-CAM-001', '测试相机', '型号A', 'available');

-- 不创建借用申请或审计记录；由实际操作产生。
COMMIT;

