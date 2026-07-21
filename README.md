# AI Study Planner Agent

当前项目完成了基础认证，以及第二阶段 A 的学习科目、学习目标、学习任务后端业务。尚未实现学习记录业务、统计、学习计划、AI、Agent、SSE和对应前端业务页面。

## 环境要求

- Java 17
- Maven 3.9+
- Node.js 20.19+ 或 22.12+
- MySQL 8.0+
- Redis 6+

请确认 Maven 使用 Java 17：

```powershell
java -version
javac -version
mvn -version
```

## 本地配置

创建数据库：

```sql
CREATE DATABASE ai_study_planner
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;
```

可以通过环境变量配置后端：

```powershell
$env:DB_URL='jdbc:mysql://localhost:3306/ai_study_planner?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia%2FShanghai&allowPublicKeyRetrieval=true&useSSL=false'
$env:DB_USERNAME='your_mysql_username'
$env:DB_PASSWORD='your_mysql_password'
$env:REDIS_HOST='localhost'
$env:REDIS_PORT='6379'
$env:REDIS_DATABASE='0'
$env:REDIS_PASSWORD=''
```

也可以将 `backend/src/main/resources/application-local.yml.example` 复制为 `backend/application-local.yml` 并填写本机配置。该文件已被Git忽略，Spring Boot通过 `spring.config.import`自动读取。

Flyway会在启动时执行 `backend/src/main/resources/db/migration` 中尚未执行的迁移。不要修改已经在数据库执行过的 `V1__create_mvp_tables.sql`。

## 启动

启动本地MySQL和Redis后运行后端：

```powershell
cd backend
mvn spring-boot:run
```

运行前端认证页面：

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

默认后端地址为 `http://localhost:8080`，前端地址为 `http://localhost:5173`。前端API地址可通过 `frontend/.env.local` 中的 `VITE_API_BASE_URL`配置，示例见 `frontend/.env.example`。

## API约定

除注册和登录外，接口都需要在请求头中携带Sa-Token返回的Token。默认请求头名称为 `satoken`。

统一响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "timestamp": 0
}
```

目标和任务分页结果：

```json
{
  "list": [],
  "page": 1,
  "pageSize": 20,
  "total": 0
}
```

`page`最小为1，`pageSize`范围为1至100。

## 认证接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/auth/register` | 注册 |
| POST | `/api/v1/auth/login` | 登录并返回Token |
| POST | `/api/v1/auth/logout` | 退出当前登录 |
| GET | `/api/v1/auth/me` | 获取当前用户 |

## 学习科目接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/subjects` | 当前用户科目列表 |
| GET | `/api/v1/subjects/{id}` | 科目详情 |
| POST | `/api/v1/subjects` | 创建科目 |
| PUT | `/api/v1/subjects/{id}` | 更新科目 |
| DELETE | `/api/v1/subjects/{id}` | 删除无关联数据的科目 |

创建或更新请求示例：

```json
{
  "name": "数学",
  "description": "高等数学",
  "color": "#409EFF",
  "sortOrder": 1
}
```

## 学习目标接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/goals` | 分页查询目标 |
| GET | `/api/v1/goals/{id}` | 目标详情和任务完成率 |
| POST | `/api/v1/goals` | 创建目标 |
| PUT | `/api/v1/goals/{id}` | 更新目标基本信息 |
| PATCH | `/api/v1/goals/{id}/status` | 修改目标状态 |
| DELETE | `/api/v1/goals/{id}` | 删除无关联任务的目标 |

列表参数：`page`、`pageSize`、`subjectId`、`status`。状态支持 `ACTIVE`、`COMPLETED`、`CANCELLED`。

创建请求示例：

```json
{
  "subjectId": 1,
  "title": "完成高等数学复习",
  "description": "复习核心章节",
  "targetMinutes": 1200,
  "targetDate": "2026-08-31",
  "status": "ACTIVE"
}
```

状态修改请求：

```json
{
  "status": "COMPLETED"
}
```

## 学习任务接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/tasks` | 分页查询任务 |
| GET | `/api/v1/tasks/{id}` | 任务详情 |
| GET | `/api/v1/tasks/today` | 今日未完成任务及预计总时长 |
| GET | `/api/v1/tasks/upcoming` | 即将截止任务 |
| POST | `/api/v1/tasks` | 创建任务，初始状态固定为TODO |
| PUT | `/api/v1/tasks/{id}` | 更新任务基本信息 |
| PATCH | `/api/v1/tasks/{id}/status` | 修改任务状态 |
| DELETE | `/api/v1/tasks/{id}` | 删除无学习记录和计划项引用的任务 |

任务列表参数：`page`、`pageSize`、`subjectId`、`goalId`、`status`、`priority`、`plannedDate`。

任务状态支持 `TODO`、`IN_PROGRESS`、`COMPLETED`、`CANCELLED`；优先级为1至4。普通创建和更新接口不接收 `completedAt`，改为 `COMPLETED` 时由后端自动设置。

创建或更新请求示例：

```json
{
  "subjectId": 1,
  "goalId": 1,
  "title": "完成极限练习",
  "description": "练习第1章题目",
  "priority": 3,
  "estimatedMinutes": 60,
  "plannedDate": "2026-07-21",
  "dueAt": "2026-07-22T20:00:00"
}
```

今日任务：

```text
GET /api/v1/tasks/today?date=2026-07-21
```

不传 `date` 时使用 `Asia/Shanghai`当天日期，只返回 `TODO` 和 `IN_PROGRESS`。

即将截止任务：

```text
GET /api/v1/tasks/upcoming?days=7
```

`days`默认为7，范围为1至30。

## 构建和测试

```powershell
cd backend
mvn clean test
mvn clean package
```

```powershell
cd frontend
npm.cmd run build
```

## 常见问题

- Maven使用错误JDK：修正 `JAVA_HOME`，并确认 `mvn -version`显示Java 17。
- MySQL连接失败：检查服务、数据库、账号权限和连接URL。
- Flyway校验失败：不要修改已执行的迁移文件；结构变更必须新增迁移版本。
- Redis连接失败：确认端口、数据库编号和密码。Redis不可用时登录不会成功。
- 请求返回401：确认请求头名称为 `satoken`，Token存在且Redis会话未过期。
- 返回403：当前登录用户试图访问其他用户的科目或目标。
- 删除返回409：资源仍被目标、任务、学习记录或计划项引用。
