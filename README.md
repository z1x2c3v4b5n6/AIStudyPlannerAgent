# AI Study Planner Agent

第一阶段仅包含工程骨架、七张MVP数据表的Flyway迁移、基础认证后端，以及登录/注册/空白首页前端。当前没有学习业务、AI、SSE或容器化功能。

## 环境要求

- Java 17
- Maven 3.9+
- Node.js 20.19+ 或 22.12+
- MySQL 8.0+
- Redis 6+

## 创建数据库

先登录本机MySQL，然后创建空数据库：

```sql
CREATE DATABASE ai_study_planner
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;
```

应用启动时Flyway会自动执行 `backend/src/main/resources/db/migration` 中的迁移。不要手工重复执行迁移脚本。

## 后端本地配置

可选择环境变量或本地配置文件，两种方式都不会要求把真实密码提交到仓库。

### 方式一：环境变量

PowerShell示例：

```powershell
$env:DB_URL='jdbc:mysql://localhost:3306/ai_study_planner?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia%2FShanghai&allowPublicKeyRetrieval=true&useSSL=false'
$env:DB_USERNAME='your_mysql_username'
$env:DB_PASSWORD='your_mysql_password'
$env:REDIS_HOST='localhost'
$env:REDIS_PORT='6379'
$env:REDIS_DATABASE='0'
$env:REDIS_PASSWORD=''
```

### 方式二：本地配置文件

将 `backend/src/main/resources/application-local.yml.example` 复制到 `backend/application-local.yml`，填写本机连接信息。根配置通过 `spring.config.import` 自动读取该文件；它已被 `.gitignore` 忽略。

## 启动本地MySQL和Redis

启动本机MySQL服务，并确认已创建 `ai_study_planner` 数据库。

Redis可以由Windows本地版本、WSL或其他本地方式启动，例如在WSL中：

```bash
sudo service redis-server start
redis-cli ping
```

返回 `PONG` 表示Redis可用。本阶段Redis只用于Sa-Token会话存储。

## 启动后端

```powershell
cd backend
mvn spring-boot:run
```

默认地址为 `http://localhost:8080`。

## 前端配置与启动

如需修改API地址，将 `frontend/.env.example` 复制为 `frontend/.env.local`。该文件已被忽略。

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

默认页面为 `http://localhost:5173`。

## 构建和测试

```powershell
cd backend
mvn test
mvn package
```

```powershell
cd frontend
npm.cmd install
npm.cmd run build
```

## 验证认证接口

以下PowerShell示例使用请求头 `satoken` 传递登录凭证。

注册：

```powershell
$body = @{username='testuser'; password='Test123456'; nickname='测试用户'} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/auth/register -ContentType application/json -Body $body
```

登录并保存Token：

```powershell
$body = @{username='testuser'; password='Test123456'} | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/auth/login -ContentType application/json -Body $body
$token = $login.data.tokenValue
```

获取当前用户：

```powershell
Invoke-RestMethod -Uri http://localhost:8080/api/v1/auth/me -Headers @{satoken=$token}
```

退出并验证Token失效：

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/auth/logout -Headers @{satoken=$token}
Invoke-RestMethod -Uri http://localhost:8080/api/v1/auth/me -Headers @{satoken=$token}
```

最后一个请求应返回401。

## 验证Sa-Token写入Redis

登录后连接对应Redis数据库：

```bash
redis-cli -n 0
SCAN 0 MATCH "satoken:*" COUNT 100
```

可以看到Sa-Token相关键。退出后，当前Token对应的登录状态应失效。不要在日志、截图或文档中公开完整Token值。

## 常见问题

- MySQL连接失败：确认服务已启动、数据库已创建、账号有建表权限，并检查URL中的时区参数。
- Flyway失败：确认数据库为空或迁移历史与当前脚本一致；不要手工创建同名表后再启动。
- Redis连接失败：确认Redis端口、数据库编号和密码；Redis不可用时登录不会返回成功。
- 前端请求401：确认浏览器本地Token存在，请求头名称为 `satoken`，且Redis中的会话尚未过期。
- 浏览器跨域失败：默认只允许 `http://localhost:5173`；可通过 `APP_CORS_ALLOWED_ORIGIN` 修改。
- Java版本错误：运行 `java -version`、`javac -version` 和 `mvn -version`，它们都应显示Java 17。
