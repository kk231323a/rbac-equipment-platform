# 设备借用与权限管理平台前端

使用 Vue 3、Vite、Element Plus、Vue Router 和 pnpm，直接请求本项目的真实 Spring Boot 后端。运行时不提供模拟接口、演示账号或伪造业务数据。

## 本地启动

前端位于 `src/main/resources/frontend`，仍是由 pnpm 和 Vite 独立运行、构建的工程，项目固定使用 pnpm 11.25.0：

```powershell
cd D:\code\IdeaProjects\rbac-system\src\main\resources\frontend
pnpm install --frozen-lockfile
pnpm dev
```

打开 Vite 输出的本地地址，默认是 `http://127.0.0.1:5173/`。Node 版本要求为 `^20.19.0 || >=22.12.0`。

开发服务器将 `/api` 代理到 `http://127.0.0.1:8080`。如需访问其他后端，在当前 PowerShell 进程设置代理目标后启动：

```powershell
$env:RBAC_API_TARGET = 'http://127.0.0.1:8080'
pnpm dev
```

后端需要 Java 17、MySQL、Redis，以及已有运行配置中的 `JWT_SECRET_BASE64` 和 `REDIS_PASSWORD`。可以直接使用 IDEA 的应用运行配置启动；命令行启动时需要先提供同样的环境变量。不要将密码、签名密钥或完整 Token 写入前端配置。

```powershell
cd D:\code\IdeaProjects\rbac-system
$env:JAVA_HOME = 'C:\Java17'
$env:Path = 'C:\Java17\bin;' + $env:Path
mvn spring-boot:run
```

`pnpm-workspace.yaml` 只允许 Vite 所需的 esbuild 执行安装构建脚本。`node_modules`、`dist` 和本地环境配置已在前端目录内忽略。

后端 Maven 资源配置排除整个 `frontend/**`，不会将前端源码、`node_modules` 或 `dist` 复制进 `target/classes` 或打入后端 JAR。后端的配置文件过滤规则以及 `mapper`、`sql` 等资源复制仍按原有约定保留。把目录放在 `src/main/resources` 下不会自动完成前端构建或静态部署。

## 页面与接口约定

- 登录页：账号密码请求 `POST /api/auth/login`，读取 `data.accessToken`。Token 和基本用户信息保存在当前标签页的 `sessionStorage`；退出调用真实 `POST /api/auth/logout`，退出失败会保留会话并显示错误，避免将本地清理误报为服务端退出成功。
- 工作台和导航：登录后及路由切换时读取 `GET /api/auth/permissions`，要求 `data` 为权限码字符串数组。权限读取失败时隐藏需要相应权限的入口，并提供重试。不存在 admin 全权限特判。
- 设备：状态筛选、分页、真实详情、新增、编辑和申请借用。编辑仅发送 `name`、`model`，设备编号与状态只读。
- 我的申请：读取普通数组，使用申请 `id` 操作，只展示后端给出的设备 ID。`pending`、`approved` 可以取消，`approved` 可以确认领用，操作最终仍由后端判定。
- 待审批：读取分页结果，以 `requestId` 批准或拒绝。应还时间保持本地 `YYYY-MM-DDTHH:mm:ss` 字符串，不转换成 UTC；审批权限使用后端实际的 `borrow:approved`。
- 归还确认：查询已领用申请，由有 `borrow:return` 权限的账号调用确认归还，不增加借用人申请归还步骤。
- 用户与角色：用户分页、启用禁用、查看已有角色、按真实角色 ID 分配及撤销角色。后端尚未提供全部角色目录，因此没有预设角色选项；已有角色列表显示真实角色 ID。当前账号不展示修改自身启用状态或撤销自身角色的按钮。
- 审计：分页读取操作人、动作、资源、结果和时间，展开后显示错误信息及操作明细。后端内容以文本渲染。

返回值按 `{ code, message, data }` 解析，分页按 `data.records`、`data.total` 解析。申请状态使用 `pending`、`approved`、`received`、`returned`、`rejected`、`cancel`。身份由后端确定，不提交 `operatorId`、审批人或借用人身份。

需要登录的请求携带 Bearer Token。401 清理登录状态并跳转登录页，403 提示权限不足并重新读取权限，409 展示后端提示并刷新当前列表。列表提供加载、空数据、失败重试，提交期间锁定重复操作。前端菜单与按钮控制用于交互提示，最终授权、资源归属和业务状态仍由后端校验。

## 构建与部署

在 `D:\code\IdeaProjects\rbac-system\src\main\resources\frontend` 中执行：

```powershell
pnpm build
pnpm preview
```

构建产物在当前前端目录的 `dist`。`preview` 仅用于本地查看构建结果。正式部署需要单独将 `dist` 内容部署在站点根路径，把 `/api` 反向代理到后端，并为 Vue Router 的 history 路由设置 `index.html` 回退；Vite 开发代理不会随静态产物部署。

## 迁移前验证记录（2026-11-19）

以下检查在前端迁移到 `src/main/resources/frontend` 之前完成：

- `pnpm install --frozen-lockfile` 和 `pnpm build` 通过。
- 新增权限接口的 4 条 MockMvc 测试通过，覆盖有效权限、空权限、匿名 401，以及同 Token 下次请求读取变更后的权限。测试使用真实 Spring Security 过滤器链和模拟服务依赖，不证明 MySQL／Redis 联调已通过。
- 真实后端启动并连接 MySQL；通过 Vite 代理访问健康接口返回 200，匿名权限查询返回 401。
- 真实登录接口验证：空白字段 400、非法 JSON 400、不存在账号 401。
- 浏览器验证空白登录提示，并检查 1280×800、390×844 登录页布局，页面宽度没有超出视口。

## 迁移后验证记录（2026-11-19）

- 在新目录执行 `pnpm install --frozen-lockfile`，由 pnpm 重建迁移后失效的 `node_modules`；Vue、Vite、Vue 插件和 Element Plus 的依赖链接均指向新目录，目标文件存在。
- `pnpm exec vite --version` 和 `pnpm build` 通过，未更改锁定的依赖版本。
- `pnpm dev --strictPort` 从新目录启动成功；`/login` 和 `/src/main.js` 的 HTTP 请求均返回 200。检查结束后已停止临时前端进程。
- 使用 Java 17 执行 `mvn -o process-resources` 成功；`target/classes` 中不存在 `frontend`，`application.yml`、三个 Mapper XML 和 `sql/RBAC.sql` 均已复制，内容保持一致。
- 本轮没有启动后端或 Redis，没有重新执行登录及业务接口联调。

按开发者要求，Redis 启动及登录后的真实业务联调留待稍后进行。尚未验证的内容包括：正确账号登录后的权限加载和退出，设备新增编辑，借用申请到审批、领用、归还的闭环，以及真实账号的角色变更和 403／409 反馈。不能将本次构建与隔离测试通过表述为这些流程已验收。

后端针对性测试命令（使用 Java 17）：

```powershell
mvn -Dtest=AuthPermissionsControllerTest test
```

新增后端测试遵循现有 `/src/test/` 忽略规则，仅保留本地；本次没有自动暂存、提交或推送。
