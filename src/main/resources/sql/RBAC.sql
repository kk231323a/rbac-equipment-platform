create database rbac;
use rbac;
CREATE TABLE equipment
(
    id          INT PRIMARY KEY AUTO_INCREMENT,
    device_no   VARCHAR(32) NOT NULL UNIQUE,
    name        VARCHAR(50) NOT NULL,
    model       VARCHAR(30),
    status      CHAR(10)    NOT NULL DEFAULT 'available',
    create_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
);
INSERT INTO equipment (device_no, name, model)
VALUES ('TEST-CAM-001', '测试相机', '型号A');
SELECT t.*
FROM rbac.equipment t;
INSERT INTO equipment (device_no, name, model)
VALUES ('TEST-CAM-001', '另一台测试相机', '型号B');
SELECT COUNT(*)
FROM equipment
WHERE device_no = 'TEST-CAM-001';
UPDATE equipment
SET name = '测试相机已修改'
WHERE device_no = 'TEST-CAM-001';
CREATE TABLE sys_user
(
    id            INT PRIMARY KEY AUTO_INCREMENT,
    account       VARCHAR(50)  NOT NULL UNIQUE,
    name          varchar(50)  not null,
    password_hash VARCHAR(255) NOT NULL,
    enable        BOOLEAN      NOT NULL DEFAULT TRUE,
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP

);
CREATE TABLE borrow_request
(
    id           INT PRIMARY KEY AUTO_INCREMENT,
    device_id    INT       NOT NULL,
    borrower_id  INT       NOT NULL,
    approver_id  INT,
    status       CHAR(10)  NOT NULL DEFAULT 'pending',
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approve_time TIMESTAMP NULL,
    due_time     TIMESTAMP NULL,
    borrow_time  TIMESTAMP NULL,
    return_time  TIMESTAMP NULL,
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_borrow_device
        FOREIGN KEY (device_id) REFERENCES equipment (id),

    CONSTRAINT fk_borrow_borrower
        FOREIGN KEY (borrower_id) REFERENCES sys_user (id),

    CONSTRAINT fk_borrow_approver
        FOREIGN KEY (approver_id) REFERENCES sys_user (id)

);
SHOW CREATE TABLE borrow_request;
SELECT id, device_no
FROM equipment;

SELECT id, account, name
FROM sys_user;
INSERT INTO sys_user (account, name, password_hash)
VALUES ('schema_test_01',
        '关联测试用户',
        'SCHEMA_TEST_ONLY_NOT_A_PASSWORD_HASH');
SELECT id, account, name
FROM sys_user
WHERE account = 'schema_test_01';

SELECT id, device_no
FROM equipment
WHERE device_no = 'TEST-CAM-001';
INSERT INTO borrow_request (device_id, borrower_id)
VALUES (1, 1);
CREATE TABLE sys_role
(
    id          INT PRIMARY KEY AUTO_INCREMENT,
    role_code   VARCHAR(30) NOT NULL UNIQUE,
    role_name   VARCHAR(20) NOT NULL,
    create_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
);
INSERT INTO sys_role (role_code, role_name)
VALUES ('borrower', '借用人'),
       ('admin', '管理员');
SELECT id, role_code, role_name
FROM sys_role;
CREATE TABLE sys_user_role
(
    id      INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    role_id INT NOT NULL,

    UNIQUE KEY uk_user_role (user_id, role_id),

    CONSTRAINT fk_user_role_user
        FOREIGN KEY (user_id) REFERENCES sys_user (id),

    CONSTRAINT fk_user_role_role
        FOREIGN KEY (role_id) REFERENCES sys_role (id)
);
create table sys_permission
(
    id              int primary key auto_increment,
    permission_code varchar(64) not null unique,
    permission_name char(20)    not null,
    create_time     timestamp   not null default current_timestamp,
    update_time     timestamp   not null default current_timestamp on update current_timestamp
);
CREATE TABLE sys_role_permission
(
    id            INT PRIMARY KEY AUTO_INCREMENT,
    role_id       INT NOT NULL,
    permission_id INT NOT NULL,

    UNIQUE KEY uk_role_permission (role_id, permission_id),

    CONSTRAINT fk_role_permission_role
        FOREIGN KEY (role_id) REFERENCES sys_role (id),

    CONSTRAINT fk_role_permission_permission
        FOREIGN KEY (permission_id) REFERENCES sys_permission (id)
);
SHOW CREATE TABLE sys_user_role;

SHOW CREATE TABLE sys_role;

SHOW CREATE TABLE sys_role_permission;

SHOW CREATE TABLE sys_permission;
-- 获取学习账号的真实用户 ID
SET @current_user_id = (SELECT id
                        FROM sys_user
                        WHERE account = 'schema_test_01');

SELECT @current_user_id AS user_id;

-- 根据用户 ID 查询权限码
SELECT DISTINCT p.permission_code
FROM sys_user_role ur
         JOIN sys_role_permission rp
              ON rp.role_id = ur.role_id
         JOIN sys_permission p
              ON p.id = rp.permission_id
WHERE ur.user_id = @current_user_id;
SELECT u.id AS user_id,
       ur.role_id,
       r.role_code,
       rp.permission_id,
       p.permission_code
FROM sys_user u
         LEFT JOIN sys_user_role ur
                   ON ur.user_id = u.id
         LEFT JOIN sys_role r
                   ON r.id = ur.role_id
         LEFT JOIN sys_role_permission rp
                   ON rp.role_id = r.id
         LEFT JOIN sys_permission p
                   ON p.id = rp.permission_id
WHERE u.id = 1;
SELECT id, role_code, role_name
FROM sys_role;

SELECT id, permission_code, permission_name
FROM sys_permission;
INSERT INTO sys_permission (permission_code,
                            permission_name)
VALUES ('equipment:read',
        '设备查询');
INSERT INTO sys_user_role (user_id, role_id)
VALUES (1, 1);
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id
FROM sys_permission
WHERE permission_code = 'equipment:read';
SELECT DISTINCT p.permission_code
FROM sys_user_role ur
         JOIN sys_role_permission rp
              ON rp.role_id = ur.role_id
         JOIN sys_permission p
              ON p.id = rp.permission_id
WHERE ur.user_id = 1;
SHOW CREATE TABLE borrow_request;

SHOW CREATE TABLE equipment;
INSERT INTO sys_permission (permission_code,
                            permission_name)
VALUES ('borrow:create', '提交借用申请');

-- role_id = 1 是当前的借用人角色
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id
FROM sys_permission
WHERE permission_code = 'borrow:create';
SELECT DISTINCT p.permission_code
FROM sys_user_role ur
         JOIN sys_role_permission rp ON rp.role_id = ur.role_id
         JOIN sys_permission p ON p.id = rp.permission_id
WHERE ur.user_id = 1;
show create table borrow_request;
insert into sys_permission(permission_code, permission_name)
VALUES ('borrow:approved', '批准借用申请');
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 2, id
FROM sys_permission
WHERE permission_code = 'borrow:approved';
CREATE INDEX idx_status_approve_time
ON rbac.bench_expiry_20261114_dee5ae00 (status, approve_time);
-- 创建新增设备权限
INSERT INTO sys_permission (permission_code, permission_name)
VALUES ('equipment:create', '新增设备');

-- 把权限分配给管理员角色
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p
    ON p.permission_code = 'equipment:create'
WHERE r.role_code = 'admin';

INSERT INTO sys_permission (permission_code, permission_name)
VALUES ('equipment:update', '修改设备');

-- 把权限分配给管理员角色
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p
    ON p.permission_code = 'equipment:update'
WHERE r.role_code = 'admin';
CREATE TABLE audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    operator_id INT NULL COMMENT '已认证操作人ID，系统或匿名为空',
    operator_type VARCHAR(16) NOT NULL DEFAULT 'user' COMMENT 'user/system/anonymous',
    operator_account VARCHAR(64) NULL COMMENT '登录尝试账号，不保存密码',
    action_code VARCHAR(64) NOT NULL COMMENT '操作标识',
    resource_type VARCHAR(32) NOT NULL COMMENT '资源类型',
    resource_id INT NULL COMMENT '资源ID，尚未创建或非法ID为空',
    result VARCHAR(16) NOT NULL COMMENT '操作结果',
    error_code INT NULL,
    error_message VARCHAR(255) NULL,
    request_path VARCHAR(255) NULL COMMENT '接口模板，不含请求体、查询串或Token',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_permission (permission_code, permission_name)
VALUES ('audit:read', '查看审计日志');

-- 把权限分配给管理员角色
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p
    ON p.permission_code = 'audit:read'
WHERE r.role_code = 'admin';

INSERT INTO sys_permission (permission_code, permission_name)
VALUES ('user:enable', '修改用户启用状态');
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p
    ON p.permission_code = 'user:enable'
WHERE r.role_code = 'admin';

INSERT INTO sys_permission (permission_code, permission_name)
VALUES ('user:disable', '禁用用户');
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p
    ON p.permission_code = 'user:disable'
WHERE r.role_code = 'admin';
START TRANSACTION;

-- 创建管理员测试账号
INSERT INTO sys_user (account, name, password_hash, enable)
SELECT 'schema_admin_01', '学习管理员', password_hash, TRUE
FROM sys_user
WHERE account = 'schema_test_01';

-- 关联已有的管理员角色
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.role_code = 'admin'
WHERE u.account = 'schema_admin_01';

-- 核对完整权限关联
SELECT u.id, u.account, u.enable,
       r.role_code, p.permission_code
FROM sys_user u
JOIN sys_user_role ur ON ur.user_id = u.id
JOIN sys_role r ON r.id = ur.role_id
JOIN sys_role_permission rp ON rp.role_id = r.id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE u.account = 'schema_admin_01'
  AND p.permission_code = 'user:enable';
commit ;

INSERT INTO sys_permission (permission_code, permission_name)
VALUES ('user:read', '查看用户');
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p
    ON p.permission_code = 'user:read'
WHERE r.role_code = 'admin';
ALTER TABLE audit_log
    ADD COLUMN details JSON NULL COMMENT '操作明细';

INSERT INTO sys_permission (permission_code, permission_name)
SELECT 'user:role:assign', '分配用户角色'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_permission
    WHERE permission_code = 'user:role:assign'
);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p
    ON p.permission_code = 'user:role:assign'
WHERE r.role_code = 'admin'
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_permission rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );

-- 初始化角色撤销权限；以下两条可重复执行。
INSERT INTO sys_permission (permission_code, permission_name)
SELECT 'user:role:revoke', '撤销用户角色'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE permission_code = 'user:role:revoke'
);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code = 'user:role:revoke'
WHERE r.role_code = 'admin'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'admin'
  AND p.permission_code = 'equipment:read'
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_permission rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );
