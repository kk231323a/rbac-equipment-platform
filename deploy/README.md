# 设备借用与权限管理平台部署说明

更新日期：2026-11-19。

本说明记录 Windows 构建、Ubuntu 虚拟机运行的单机演示部署。Nginx 提供前端静态文件并将 /api/ 请求转发到 Spring Boot；后端通过 Docker 网络连接 MySQL 和 Redis。当前使用四个容器和手动命令，没有 Docker Compose 文件。

## 1. 环境和文件

- Windows 工程：D:\code\IdeaProjects\rbac-system；Java 17、Maven 3.9.13；前端使用 pnpm。
- 虚拟机：Ubuntu Server 24.04.5 LTS，普通用户 kk，时区 Asia/Shanghai。
- 本次虚拟机地址：192.168.150.102，VMware NAT 网络；旧 CentOS 的 192.168.150.101 不用于本次部署。
- Docker Engine：29.10.0。下文使用 sudo docker，不要求把用户加入 docker 组。
- 后端基础镜像：eclipse-temurin:17-jre-jammy；应用镜像：rbac-backend:0.1。
- 依赖镜像：mysql:8.0.43、redis:8.10.3、nginx:1.30.7。这些是本次使用版本，后续升级需要重新验证。
- 网络：rbac-net；命名数据卷：rbac-mysql-data、rbac-redis-data。
- 容器：rbac-mysql、rbac-redis、rbac-backend、rbac-nginx。

仓库中的部署文件：

- 根目录 Dockerfile：将 Maven 产物复制进 Java 17 运行镜像，JVM 使用 Asia/Shanghai。
- deploy/nginx/default.conf：静态资源、Vue 路由回退和 API 反向代理。
- deploy/mysql/001-init.sql：空数据库初始化。
- 本文件：构建、传输、配置和验证步骤。

原有 src/main/resources/sql/RBAC.sql 包含历次学习和实验 SQL，不作为初始化脚本执行。新脚本不包含压测表，也未额外添加尚未在实际业务表上验证的索引。

以下按新环境说明。已有同名容器、网络或数据卷时，先检查状态，跳过相应创建步骤，不要为了重跑命令删除数据卷。IP 来自 DHCP，地址改变后需要同步调整 SSH、端口绑定和访问地址。

## 2. 先选择数据来源

只能选择其中一种方式：

1. **空库演示部署**。执行 `deploy/mysql/001-init.sql`，生成 9 张表和最小演示数据。
2. **迁移学习进度**。按第 10 节导出并导入当前版本的 9 张表，使用原库的数据作为目标环境的起点。

当前 192.168.150.102 环境采用数据迁移，已经有申请和审计记录，不需要重新初始化。

001-init.sql 不包含 CREATE DATABASE、USE、DROP TABLE 或清空数据的语句，由命令选择目标空库。脚本只执行一次，重复执行应报错，不要使用 --force 忽略错误。建表语句会隐式提交，末尾事务只覆盖种子数据，无法回滚已创建的表。导入失败时先检查第一条错误及目标库状态。

空库脚本创建的两个账号：

- 借用人：schema_test_01 / TestPassword-123。
- 管理员：schema_admin_01 / TestPassword-123。

两个密码分别生成 BCrypt 哈希。借用人角色有 `equipment:read` 和 `borrow:create`。管理员角色有 14 项权限，包含设备管理、审批、确认归还、审计查看、创建用户（`user:create`）、用户角色管理和角色权限授予及撤销（`role:permission:assign`、`role:permission:revoke`）。查询目录沿用 `user:read`，审批使用 `borrow:approved`。管理员需要借用功能时，可为该用户同时分配 `borrower` 角色。

升级已有数据库时，先检查 `auth_state_sync_task` 表、所需权限码及其与 `admin` 的关联。缺项按初始化脚本中的对应结构或种子配置单独补齐。

平台账号与 Linux 用户 kk、MySQL root、数据库应用用户 rbac_app 分别独立。演示账号仅由空库脚本创建；迁移数据不会自动重置原账号密码。

## 3. 构建并传输文件

Ubuntu 需要安装 Docker 和 OpenSSH。Docker 安装参见 [官方 Ubuntu 安装文档](https://docs.docker.com/engine/install/ubuntu/)。

Ubuntu 创建目录：

~~~bash
mkdir -p ~/rbac-deploy/backend/target
mkdir -p ~/rbac-deploy/mysql
mkdir -p ~/rbac-deploy/redis
mkdir -p ~/rbac-deploy/frontend/html
mkdir -p ~/rbac-deploy/nginx
chmod 700 ~/rbac-deploy/mysql ~/rbac-deploy/redis
sudo timedatectl set-timezone Asia/Shanghai
~~~

Windows PowerShell 构建。前端源码位于 src/main/resources/frontend：

~~~powershell
$rbacRepoRoot = 'D:\code\IdeaProjects\rbac-system'
$env:JAVA_HOME = 'C:\Java17'
$env:Path = 'C:\Java17\bin;' + $env:Path

Set-Location -LiteralPath $rbacRepoRoot
mvn "-DskipTests" package
if ($LASTEXITCODE -ne 0) { throw '后端打包失败' }

Set-Location -LiteralPath "$rbacRepoRoot\src\main\resources\frontend"
pnpm install --frozen-lockfile
if ($LASTEXITCODE -ne 0) { throw '前端依赖安装失败' }
pnpm build
if ($LASTEXITCODE -ne 0) { throw '前端构建失败' }

$rbacDeployStamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$rbacBuildDir = Join-Path $env:TEMP "rbac-deploy-$rbacDeployStamp"
New-Item -ItemType Directory -Path $rbacBuildDir | Out-Null
$rbacFrontendArchive = Join-Path $rbacBuildDir 'frontend.tar.gz'
tar -czf $rbacFrontendArchive -C "$rbacRepoRoot\src\main\resources\frontend\dist" .
if ($LASTEXITCODE -ne 0) { throw '前端归档失败' }

scp "$rbacRepoRoot\Dockerfile" "kk@192.168.150.102:rbac-deploy/backend/Dockerfile"
scp "$rbacRepoRoot\target\rbac-system-0.0.1-SNAPSHOT.jar" "kk@192.168.150.102:rbac-deploy/backend/target/"
scp "$rbacRepoRoot\deploy\nginx\default.conf" "kk@192.168.150.102:rbac-deploy/nginx/default.conf"
scp "$rbacRepoRoot\deploy\mysql\001-init.sql" "kk@192.168.150.102:rbac-deploy/mysql/001-init.sql"
scp $rbacFrontendArchive "kk@192.168.150.102:rbac-deploy/frontend/frontend.tar.gz"
~~~

目标端口默认 SSH 22。后端打包暂缓独立测试，不能把 -DskipTests 的打包成功当成功能测试通过。

## 4. 网络、MySQL 和初始化

以下在 Ubuntu 执行：

~~~bash
RBAC_IP=192.168.150.102
sudo docker network create rbac-net
sudo docker volume create rbac-mysql-data
sudo docker volume create rbac-redis-data
sudo docker pull mysql:8.0.43
sudo docker pull redis:8.10.3
sudo docker pull eclipse-temurin:17-jre-jammy
sudo docker pull nginx:1.30.7
~~~

RBAC_IP 是当前终端变量，换终端后需要重新设置。使用 nano 编辑 ~/rbac-deploy/mysql/mysql.env，将下列占位值换成自己选定的密码：

~~~dotenv
MYSQL_ROOT_PASSWORD=CHANGE_ME_MYSQL_ROOT_PASSWORD
MYSQL_DATABASE=rbac
MYSQL_USER=rbac_app
MYSQL_PASSWORD=CHANGE_ME_MYSQL_APP_PASSWORD
~~~

~~~bash
chmod 600 ~/rbac-deploy/mysql/mysql.env
sudo docker run -d \
  --name rbac-mysql \
  --network rbac-net \
  --restart unless-stopped \
  --env-file "$HOME/rbac-deploy/mysql/mysql.env" \
  -p "$RBAC_IP:3306:3306" \
  --mount type=volume,source=rbac-mysql-data,target=/var/lib/mysql \
  mysql:8.0.43 \
  --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_0900_ai_ci

sudo docker logs --tail 50 rbac-mysql
~~~

等待 MySQL 就绪。MYSQL_* 变量只在空数据目录首次初始化时创建数据库和用户；复用数据卷时，修改这些变量不会重置库或密码。[MySQL 官方镜像说明](https://hub.docker.com/_/mysql)

3306 绑定到虚拟机地址，便于 Windows DataGrip 连接。后端通过容器名访问 MySQL。

**仅空库方式**执行下面的导入；迁移方式改用第 10 节：

~~~bash
sudo docker cp ~/rbac-deploy/mysql/001-init.sql rbac-mysql:/tmp/001-init.sql
sudo docker exec -it rbac-mysql \
  mysql --default-character-set=utf8mb4 --batch \
  -u rbac_app -p rbac \
  --execute="source /tmp/001-init.sql"
~~~

密码提示处输入 MYSQL_PASSWORD 的值。再连接数据库检查：

~~~bash
sudo docker exec -it rbac-mysql \
  mysql --default-character-set=utf8mb4 -u rbac_app -p rbac
~~~

在 mysql> 提示符执行：

~~~sql
SHOW TABLES;
SELECT role_code, role_name FROM sys_role;
SELECT account, name, enable FROM sys_user;
SELECT COUNT(*) AS permission_count FROM sys_permission;
SELECT COUNT(*) AS role_permission_count FROM sys_role_permission;
SELECT device_no, status FROM equipment;
SELECT COUNT(*) AS request_count FROM borrow_request;
SELECT COUNT(*) AS audit_count FROM audit_log;
SELECT COUNT(*) AS auth_sync_task_count FROM auth_state_sync_task;
exit
~~~

按当前脚本静态核对，空库导入后预期有 9 张表、2 个账号、2 个角色、15 项权限、16 条角色权限关联和 1 台 `available` 设备。应用启动前，借用申请、审计日志和改密同步任务均为 0 条。当前脚本待重新导入空库验证。

## 5. Redis

使用 nano 编辑 ~/rbac-deploy/redis/redis.conf，替换密码占位值：

~~~conf
bind 0.0.0.0
protected-mode yes
port 6379
requirepass "CHANGE_ME_REDIS_PASSWORD"
dir /data
appendonly yes
appendfsync everysec
daemonize no
~~~

~~~bash
chmod 644 ~/rbac-deploy/redis/redis.conf
sudo docker run -d \
  --name rbac-redis \
  --network rbac-net \
  --restart unless-stopped \
  --mount "type=bind,source=$HOME/rbac-deploy/redis/redis.conf,target=/usr/local/etc/redis/redis.conf,readonly" \
  --mount type=volume,source=rbac-redis-data,target=/data \
  redis:8.10.3 \
  redis-server /usr/local/etc/redis/redis.conf

sudo docker exec -it rbac-redis redis-cli --askpass PING
~~~

输入 Redis 密码后应返回 PONG。配置文件允许容器内 Redis 用户读取；外层目录的 700 权限限制宿主机其他普通用户访问。Redis 只在 rbac-net 内提供服务，没有发布 6379 到宿主机。

AOF 和命名卷用于保留撤销记录；appendfsync everysec 在异常断电时仍可能损失约一秒的数据，不能表述为零丢失。[Redis 官方镜像说明](https://hub.docker.com/_/redis)

## 6. 后端

使用 nano 编辑 ~/rbac-deploy/backend/backend.env，替换数据库和 Redis 密码占位值：

~~~dotenv
SPRING_DATASOURCE_URL=jdbc:mysql://rbac-mysql:3306/rbac?connectionTimeZone=GMT%2B08:00&forceConnectionTimeZoneToSession=true&useSSL=false&allowPublicKeyRetrieval=true
SPRING_DATASOURCE_USERNAME=rbac_app
SPRING_DATASOURCE_PASSWORD=CHANGE_ME_MYSQL_APP_PASSWORD
SPRING_DATA_REDIS_HOST=rbac-redis
SPRING_DATA_REDIS_PORT=6379
REDIS_PASSWORD=CHANGE_ME_REDIS_PASSWORD
JWT_TTL=30m
~~~

数据库密码与 MYSQL_PASSWORD 一致，Redis 密码与 requirepass 一致。env 文件值不加外层引号，不要把文件当作 Bash 脚本 source；URL 中的 & 原样保存。环境变量覆盖工程本地开发配置。[Spring Boot 外部配置文档](https://docs.spring.io/spring-boot/3.5/reference/features/external-config.html)

首次配置时追加 JWT 密钥：

~~~bash
printf '\nJWT_SECRET_BASE64=%s\n' "$(openssl rand -base64 32)" >> ~/rbac-deploy/backend/backend.env
chmod 600 ~/rbac-deploy/backend/backend.env
~~~

只生成一次，后续复用文件，不要每次启动重复追加。新环境采用自己的密钥，旧环境 Token 不会被新后端接受。密码、密钥和完整 Token 不纳入 Git。--env-file 的变量仍可被有 Docker 管理权限的人查看，这里用于本机演示。

容器的 localhost 指向容器自身，因此连接依赖使用同一网络下的容器名。自定义 bridge 网络支持容器名解析。[Docker bridge 文档](https://docs.docker.com/engine/network/drivers/bridge/)

Dockerfile 将 JVM 时区设为 Asia/Shanghai；JDBC 的 connectionTimeZone=GMT%2B08:00 和 forceConnectionTimeZoneToSession=true 约束连接及数据库会话时区，避免默认 UTC 与 LocalDateTime/TIMESTAMP 混用造成八小时时差。偏移量不依赖 MySQL 命名时区表。[Connector/J 日期时间配置](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-datetime-types-processing.html)

~~~bash
cd ~/rbac-deploy/backend
sudo docker build -t rbac-backend:0.1 .

sudo docker run -d \
  --name rbac-backend \
  --network rbac-net \
  --restart unless-stopped \
  --env-file "$HOME/rbac-deploy/backend/backend.env" \
  -p "$RBAC_IP:8080:8080" \
  rbac-backend:0.1

sudo docker logs --tail 100 -f rbac-backend
~~~

看到 Started RbacSystemApplication 后，Ctrl+C 仅结束日志跟随，容器继续运行。启动成功不足以验证整条认证链路。

应用启动后执行一次超时取消任务，之后每天上海时间 12:00 执行。迁移过来的已批准且逾期未领用申请可能被任务取消并写入审计，核对迁移结果时需要考虑这类业务变化。

## 7. 前端和 Nginx

~~~bash
tar -xzf ~/rbac-deploy/frontend/frontend.tar.gz \
  -C ~/rbac-deploy/frontend/html
chmod -R a+rX ~/rbac-deploy/frontend/html

sudo docker run --rm \
  --network rbac-net \
  --mount "type=bind,source=$HOME/rbac-deploy/nginx/default.conf,target=/etc/nginx/conf.d/default.conf,readonly" \
  nginx:1.30.7 nginx -t
~~~

配置检查通过后启动正式容器：

~~~bash
sudo docker run -d \
  --name rbac-nginx \
  --network rbac-net \
  --restart unless-stopped \
  -p "$RBAC_IP:80:80" \
  --mount "type=bind,source=$HOME/rbac-deploy/nginx/default.conf,target=/etc/nginx/conf.d/default.conf,readonly" \
  --mount "type=bind,source=$HOME/rbac-deploy/frontend/html,target=/usr/share/nginx/html,readonly" \
  nginx:1.30.7
~~~

浏览器访问 http://192.168.150.102/ 。前端采用相对 /api 地址，与页面同源；Vite 开发代理在 dist 部署时不生效。

现有配置将 /api/ 原样转发到 rbac-backend:8080，proxy_pass 地址末尾没有斜杠。/assets/ 中缺失资源返回 404；其他前端路径回退到 index.html，支持 /equipment 等页面直接访问和刷新。[Nginx proxy_pass](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_pass)、[Vue Router history 模式](https://router.vuejs.org/guide/essentials/history-mode.html)

--rm 使一次性配置检查容器在命令结束后自动移除，不移除业务容器或数据卷。

## 8. 验证

Windows PowerShell：

~~~powershell
$rbacBaseUrl = 'http://192.168.150.102'
curl.exe -i "$rbacBaseUrl/api/health"
curl.exe -i "$rbacBaseUrl/api/auth/permissions"

$rbacLoginBody = @{
    account = 'schema_test_01'
    password = 'TestPassword-123'
} | ConvertTo-Json
$rbacLogin = Invoke-RestMethod `
    -Uri "$rbacBaseUrl/api/auth/login" `
    -Method Post `
    -ContentType 'application/json' `
    -Body $rbacLoginBody
$rbacLogin.code

$rbacHeaders = @{
    Authorization = "Bearer $($rbacLogin.data.accessToken)"
}
Invoke-RestMethod `
    -Uri "$rbacBaseUrl/api/auth/permissions" `
    -Headers $rbacHeaders
~~~

健康检查应返回 HTTP 200 和 status=ok；未登录权限查询应返回 HTTP 401；正确登录应返回业务 code=200，携带 Token 后应查到借用人的权限。不要打印或提交完整登录响应和 Authorization 请求头。

`/api/health` 只检查应用是否存活。实际登录并携带 Access Token 查询权限，可以覆盖 MySQL 凭据校验，以及 Redis 用户状态、会话、撤销记录和权限读取。登录响应包含 `accessToken` 和 `refreshToken`。调用 `POST /api/auth/refresh` 时，在 JSON 请求体中提交 `refreshToken`，成功后会收到新的一对 Token。

浏览器再检查登录、设备查询、刷新 /equipment 和切换账号走主流程。借用人访问管理接口应被拒绝为 403。完整业务验收还需核对申请、设备和审计最终数据及重复/并发操作，不能只凭首页可访问宣布完成。

## 9. 已验证范围及排查

截至 2026-11-24，按版本记录的验证情况如下。

- 已有验证：Java 17 执行 mvn "-DskipTests" package 成功，前端 pnpm build 成功，JAR 未打入前端目录。
- 开发者反馈：MySQL 迁移、Redis 认证 PING、后端构建与启动、登录及带 Token 权限查询、浏览器登录和刷新符合预期。
- 从 Windows 实际请求当前虚拟机 Nginx：/ 返回 200 HTML，/equipment 返回 200 HTML，缺失 /assets/ 资源返回 404，/api/health 返回 200 JSON，未带 Token 访问 /api/auth/permissions 返回 401。
- JDBC 时区参数已用当前驱动连接本机 MySQL 8.0.43 核验，数据库会话为 +08:00；不等于已检查虚拟机的所有运行连接。
- 初始化脚本初始版本（12 项权限、13 条角色权限关联）曾在本机 MySQL 8.0.43 的独立空库 rbac_init_verify_20261119_670e5d，使用 mysql --batch --execute="source ..." 实际导入，退出码为 0。8 张表结构与业务库一致，种子行数、两个 BCrypt 密码及独立盐值、角色/用户权限集合均通过核对；非法外键和重复用户角色分别被错误码 1452、1062 拒绝。
- 上述初始版本重复导入在首张已存在的表处返回 1050、进程退出码 1，停止后种子数据未变。验证前后原 rbac 的 8 张表数据指纹一致；原验证库保留供检查。
- 当前空库脚本已补充 `auth_state_sync_task` 和管理员的 `user:create`，9 张表、15 项权限、16 条关联为静态核对结果，待重新导入空库验证。
- 角色和权限目录、角色权限查询及授予和撤销接口于 2026-11-19 完成源码检查，并使用 Java 17 编译打包通过。角色权限变更的回滚和缓存刷新已经过替身验证，最新版在虚拟机上的真实授权、撤权和改密补偿仍待验收。
- 全新 Docker 环境的部署、双账号借用业务闭环和容器重启恢复仍待验收。

常见排查：

- Docker socket permission denied：使用 sudo docker，不需要放开 socket 权限。
- 连接依赖失败：检查四个容器是否都在 rbac-net，后端是否误连 localhost 或旧地址 192.168.150.101。
- 健康检查成功但认证失败：检查 MySQL/Redis 日志和对应密码，不把健康检查当作依赖检查。
- 时间差八小时：检查后端实际加载的 JDBC URL 和镜像 JVM 时区；修改 env 文件不会改变运行中容器的环境变量。
- Nginx 502：检查后端就绪及两端日志。后端重建后 IP 可能改变，当前配置可能保留旧解析；后端就绪后执行 sudo docker exec rbac-nginx nginx -s reload。
- 刷新子路径 404：核对 Nginx 配置和挂载目录。html 目录应直接包含 index.html、assets，不能外包一层 dist。
- 修改 Nginx 配置：先 sudo docker exec rbac-nginx nginx -t，通过再 reload。
- sudo docker restart rbac-backend 复用原容器和原环境变量；它不会自动读取已修改的 backend.env。更新镜像或环境变量需要另行安排容器重建。

## 10. 可选：迁移现有数据

此流程用于把学习数据导入目标空库。只迁移 MySQL 并使用新 Redis 时，先暂停源环境的业务写入，保持原后端和 Redis 运行，等待改密定时任务处理完已有记录。补偿会读取源 Redis 中的认证状态，因此先确认 `auth_state_sync_task` 为 0 条，再停止原后端并导出。导出时保持源 MySQL 运行。部署后在目标环境重新登录。

~~~sql
SELECT COUNT(*) AS pending_auth_sync FROM auth_state_sync_task;
~~~

Windows PowerShell：

~~~powershell
$rbacMigrationStamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$rbacMigrationDir = Join-Path $env:USERPROFILE "rbac-migration-$rbacMigrationStamp"
New-Item -ItemType Directory -Path $rbacMigrationDir | Out-Null
$rbacDumpFile = Join-Path $rbacMigrationDir 'rbac.sql'

& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe' `
    --host=localhost --port=3306 --user=root --password `
    --default-character-set=utf8mb4 `
    --single-transaction `
    --no-tablespaces `
    --set-gtid-purged=OFF `
    --skip-add-drop-table `
    --skip-add-locks `
    "--result-file=$rbacDumpFile" `
    rbac equipment sys_user sys_role sys_permission sys_user_role `
    sys_role_permission borrow_request audit_log auth_state_sync_task
if ($LASTEXITCODE -ne 0) { throw '导出失败' }

scp $rbacDumpFile "kk@192.168.150.102:rbac-deploy/mysql/rbac.sql"
~~~

提示处输入源数据库密码。`--result-file` 让 `mysqldump` 直接写文件，保留输出编码。`--single-transaction` 对 InnoDB 表建立一致性快照，导出期间避免修改表结构。命令明确列出当前版本的 9 张表。 [mysqldump 文档](https://dev.mysql.com/doc/refman/8.0/en/mysqldump.html)

Ubuntu 导入：

~~~bash
chmod 600 ~/rbac-deploy/mysql/rbac.sql
sudo docker cp ~/rbac-deploy/mysql/rbac.sql rbac-mysql:/tmp/rbac.sql
sudo docker exec -it rbac-mysql \
  mysql --default-character-set=utf8mb4 --batch \
  -u rbac_app -p rbac \
  --execute="source /tmp/rbac.sql"
~~~

导入后对照源库，核对 9 张表的行数和关键状态。导出包含账号哈希、申请和审计等历史数据，保存在仓库外。 [SQL 格式导出文件的恢复](https://dev.mysql.com/doc/refman/8.0/en/reloading-sql-format-dumps.html)

