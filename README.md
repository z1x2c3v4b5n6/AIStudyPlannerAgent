# AI Study Planner Agent

AI 学习规划 Agent 的前后端分离 MVP。当前已完成基础认证、学习业务管理、学习记录与统计、规则学习计划，以及基于 DeepSeek 的 AI 学习计划草案前后端入口。尚未实现 Agent、SSE、RAG、MCP 和会话历史。

## 环境要求

- Java 17
- Maven 3.9+
- Node.js 20.19+ 或 22.12+
- MySQL 8.0+
- Redis 6+（Windows 可使用 Memurai）

数据库名为 `ai_study_planner`，字符集建议使用 `utf8mb4`。后端可通过以下环境变量配置：

```powershell
$env:DB_URL='jdbc:mysql://localhost:3306/ai_study_planner?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia%2FShanghai&allowPublicKeyRetrieval=true&useSSL=false'
$env:DB_USERNAME='your_mysql_username'
$env:DB_PASSWORD='your_mysql_password'
$env:REDIS_HOST='localhost'
$env:REDIS_PORT='6379'
$env:REDIS_DATABASE='0'
$env:REDIS_PASSWORD=''
```

也可以将 `backend/src/main/resources/application-local.yml.example` 复制为 `backend/application-local.yml` 并填写本地配置。该文件已被 Git 忽略。Flyway 会在后端启动时执行尚未运行的迁移，不要修改已经执行过的迁移文件。

## 本地启动与构建

启动 MySQL 和 Redis/Memurai 后运行后端：

```powershell
cd backend
mvn spring-boot:run
```

安装依赖并启动前端：

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

前端生产构建：

```powershell
cd frontend
npm.cmd run build
```

默认后端地址为 `http://localhost:8080`，前端地址为 `http://localhost:5173`。可在 `frontend/.env.local` 中通过 `VITE_API_BASE_URL` 修改前端 API 地址。

## 前端页面

| 路径 | 页面 | 功能 |
|---|---|---|
| `/login` | 登录 | 登录并保存 Sa-Token |
| `/register` | 注册 | 创建用户账号 |
| `/` | 学习概览 | 科目、进行中目标、今日待办统计，含逾期任务快捷操作和未来 7 天到期任务 |
| `/subjects` | 科目管理 | 科目列表、创建、编辑和删除 |
| `/goals` | 学习目标 | 分页筛选、创建、编辑、状态变更、详情进度和删除 |
| `/tasks` | 学习任务 | 多条件筛选、创建、编辑、状态变更、详情和删除 |
| `/plans` | 学习计划 | AI智能规划与规则规划切换、草案调整确认、历史查询和执行状态跟踪 |
| `/records` | 学习记录 | 分页筛选、创建、编辑、清空任务或反馈、详情和删除 |
| `/statistics` | 数据统计 | 日期范围查询、统计概览、每日趋势和科目时长分布 |

认证后的页面共用主布局。桌面端使用侧边导航，移动端使用抽屉导航。所有业务数据均来自真实后端接口，不使用模拟数据。

## API 约定

除注册和登录外，接口都需要携带 Sa-Token 返回的动态请求头，默认请求头名称为 `satoken`。统一响应结构：

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "timestamp": 0
}
```

目标和任务分页结果包含 `list`、`page`、`pageSize` 和 `total`；`page` 最小为 1，`pageSize` 范围为 1 至 100。

### 认证接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/auth/register` | 注册 |
| POST | `/api/v1/auth/login` | 登录并返回 Token 与用户信息 |
| POST | `/api/v1/auth/logout` | 退出当前登录 |
| GET | `/api/v1/auth/me` | 获取当前用户 |

### 学习科目接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/subjects` | 当前用户的科目列表 |
| GET | `/api/v1/subjects/{id}` | 科目详情 |
| POST | `/api/v1/subjects` | 创建科目 |
| PUT | `/api/v1/subjects/{id}` | 更新科目 |
| DELETE | `/api/v1/subjects/{id}` | 删除无关联数据的科目 |

科目写入字段为 `name`、`description`、`color`、`sortOrder`。

### 学习目标接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/goals` | 分页查询目标，可按 `subjectId`、`status` 筛选 |
| GET | `/api/v1/goals/{id}` | 目标详情及任务完成率 |
| POST | `/api/v1/goals` | 创建目标 |
| PUT | `/api/v1/goals/{id}` | 更新目标基本信息 |
| PATCH | `/api/v1/goals/{id}/status` | 单独修改目标状态 |
| DELETE | `/api/v1/goals/{id}` | 删除无关联任务的目标 |

目标状态包括 `ACTIVE`、`COMPLETED`、`CANCELLED`。普通更新接口不接收状态。

### 学习任务接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/tasks` | 分页查询任务，支持科目、目标、状态、优先级和计划日期筛选 |
| GET | `/api/v1/tasks/{id}` | 任务详情 |
| GET | `/api/v1/tasks/today` | 计划日期不晚于今天的未完成待办及预计总时长 |
| GET | `/api/v1/tasks/upcoming?days=7` | 未来指定天数即将截止的任务 |
| POST | `/api/v1/tasks` | 创建任务，初始状态为 `TODO` |
| PUT | `/api/v1/tasks/{id}` | 更新任务基本信息 |
| PATCH | `/api/v1/tasks/{id}/status` | 单独修改任务状态 |
| DELETE | `/api/v1/tasks/{id}` | 删除无学习记录或计划项引用的任务 |

任务状态包括 `TODO`、`IN_PROGRESS`、`COMPLETED`、`CANCELLED`，优先级为 1 至 4。普通创建和更新接口不接收 `status`、`completedAt`；完成时间由后端维护。

### 学习计划接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/plans/draft` | 根据未完成任务生成确定性规则草案，不写数据库 |
| POST | `/api/v1/plans/confirm` | 重新校验草案并事务保存，相同 `draftId` 幂等 |
| GET | `/api/v1/plans` | 按日期和状态分页查询当前用户计划 |
| GET | `/api/v1/plans/{id}` | 查询计划及完整计划项 |
| PATCH | `/api/v1/plans/{id}/status` | 取消已确认计划 |
| PATCH | `/api/v1/plans/{planId}/items/{itemId}/status` | 完成、跳过或恢复计划项 |

规则规划模式使用稳定规则生成：逾期和临近截止任务优先，其次按照优先级、计划日期、进行中状态和任务 ID 排序。草案只存在于接口响应和前端状态中，用户确认后才写入 `study_plan` 与 `study_plan_item`。计划项全部完成或跳过时计划自动完成，恢复任一计划项后计划回到已确认。计划项状态不会自动修改原学习任务状态。

### AI 学习计划草案

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/ai/plans/draft` | 使用 DeepSeek 生成结构化学习计划草案；AI 不可用或输出不合法时自动返回规则草案 |

请求语义与 `/api/v1/plans/draft` 一致：

```json
{
  "planDate": "2026-07-23",
  "startTime": "09:00:00",
  "availableMinutes": 180,
  "requirement": "优先复习 Java 集合和数据库"
}
```

响应会额外给出 `generatorType`、`provider`、`model`、`fallbackUsed` 和安全的 `fallbackReason`。`draft` 仍使用原有草案结构，可直接提交到 `POST /api/v1/plans/confirm`。AI 只选择候选任务、建议分钟数和理由；后端负责计算顺序及连续的起止时间，并校验任务归属、状态、重复项、时长和跨日约束。草案不会自动入库，必须由用户确认后保存。

后端使用 Spring AI `1.1.8`，通过 OpenAI 兼容协议连接 DeepSeek，默认模型为 `deepseek-v4-flash`。当前 AI 规划会在请求顶层显式发送 `thinking.type=disabled`，使用非思考模式生成结构化计划。AI 默认关闭且 API Key 不写入仓库：

```powershell
$env:AI_ENABLED='true'
$env:DEEPSEEK_API_KEY='your-key'
$env:DEEPSEEK_BASE_URL='https://api.deepseek.com'
$env:DEEPSEEK_COMPLETIONS_PATH='/chat/completions'
$env:DEEPSEEK_MODEL='deepseek-v4-flash'
$env:DEEPSEEK_TEMPERATURE='0.2'
$env:DEEPSEEK_MAX_TOKENS='2500'
$env:DEEPSEEK_TIMEOUT_SECONDS='45'
```

未配置 Key 时应用仍能启动，AI 草案接口会自动使用现有规则生成器。模型超时、限流、服务异常、空响应、非法 JSON 或业务校验失败也会降级并正常返回可用草案，不暴露第三方原始异常。

`/plans` 页面默认使用“AI智能规划”，也可以主动切换为“规则规划”。AI成功时草案区域显示DeepSeek及模型信息；AI降级时显示安全的降级原因，但降级得到的规则草案仍可继续排序、调整时长、恢复原始结果并通过原确认接口保存。历史计划没有来源字段，因此历史列表不会推断或伪造AI来源。

### 学习记录接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/records` | 分页查询当前用户学习记录 |
| GET | `/api/v1/records/{id}` | 查询学习记录详情 |
| POST | `/api/v1/records` | 创建学习记录并由后端计算时长 |
| PUT | `/api/v1/records/{id}` | 更新学习记录并重新计算时长 |
| DELETE | `/api/v1/records/{id}` | 删除当前用户学习记录 |

分页查询支持 `subjectId`、`taskId`、`startDate` 和 `endDate`。日期格式为 `YYYY-MM-DD`，记录按照开始时间和 ID 倒序返回。`startDate` 包含当天零点，`endDate` 包含当天整天。

创建或更新示例：

```json
{
  "subjectId": 1,
  "taskId": 10,
  "startedAt": "2026-07-21T19:00:00",
  "endedAt": "2026-07-21T20:15:00",
  "feedback": "完成了极限章节练习"
}
```

调用方不能提交 `durationMinutes`。后端使用 `ChronoUnit.MINUTES` 计算学习时长，单条记录必须为 1 至 1440 分钟，结束时间不得晚于当前上海时间。创建记录不会自动完成关联任务。

返回示例：

```json
{
  "id": 1,
  "subjectId": 1,
  "subjectName": "高等数学",
  "subjectColor": "#409EFF",
  "taskId": 10,
  "taskTitle": "完成极限练习",
  "startedAt": "2026-07-21T19:00:00",
  "endedAt": "2026-07-21T20:15:00",
  "durationMinutes": 75,
  "feedback": "完成了极限章节练习"
}
```

### 基础统计接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/statistics/summary` | 总时长、记录数、活跃天数和平均时长 |
| GET | `/api/v1/statistics/daily-trend` | 按日期升序返回每日学习趋势并补齐零值日期 |
| GET | `/api/v1/statistics/subject-distribution` | 按学习时长返回科目分布和百分比 |

三个接口统一支持 `startDate`、`endDate`。不传日期时默认查询包含今天的最近 7 天；开始、结束日期均包含，范围最多 366 天。统计仅计算当前登录用户的数据。

概览返回示例：

```json
{
  "startDate": "2026-07-15",
  "endDate": "2026-07-21",
  "totalMinutes": 420,
  "recordCount": 8,
  "activeDays": 5,
  "averageDailyMinutes": 60.0,
  "averageMinutesPerActiveDay": 84.0
}
```

## 后端构建与测试

```powershell
cd backend
mvn clean test
mvn clean package
```

## 第三阶段 B 前端说明

学习记录页面沿用后端计算时长的原则。前端只提交科目、可选任务、开始时间、结束时间和反馈，不提交 `durationMinutes`。日期时间使用 `YYYY-MM-DDTHH:mm:ss` 本地格式，不使用 `toISOString()`。编辑时可以把任务改为“不关联任务”，也可以清空原反馈；前端会明确提交 `null`。

前端会实时预览学习时长，并统一按北京时间检查结束时间晚于开始时间、至少 1 分钟、不超过 24 小时且不晚于当前时间。MVP 不允许记录跨越自然日；后端还会拒绝与当前用户已有记录重叠的时间段。最终校验和时长以后端为准。

统计页面默认查询包含今天的最近 7 天，也支持最近 30 天、90 天和最多 366 天的自定义范围。三个统计接口分别维护加载和失败状态，单个接口失败不会阻塞整个页面。

图表使用 ECharts，并从 `echarts/core` 按需注册折线图、柱状图、饼图、提示框、图例、网格和 Canvas 渲染器。每日趋势同时展示学习分钟和记录数；科目分布同时提供环形图与文本列表。

当前已完成学习记录、基础统计、今日学习计划草案与确认保存的前端，以及 DeepSeek/Spring AI 非流式计划草案后端。后续仍未实现：

- SSE 流式输出
- Agent 工具调用与 Function Calling
- RAG、MCP 和 AI 会话历史
