# C-9 Adaptive Learning Agent

C-9 adds a learning-insights page and calls the same services from the Agent. The C-4 keyword retriever remains the production retriever. C-8 workflows and existing task, plan, record, and path endpoints remain available.

## Data and permissions

| Capability | Data and interpretation |
| --- | --- |
| Learning profile | Computes study duration, active days, daily trend, subject time, upcoming workload and backlog on demand. The default window is 14 days and explicit windows have a 90-day limit. The completion rate uses current due dates in the selected window and may change if a deadline is edited. |
| Diagnostics | Four versioned rules report continuity, backlog, paired planned-vs-actual duration and capacity. A capacity warning requires a user-entered budget. An absent or unpaired time sample stays visible as insufficient evidence. |
| Review | Uses real task IDs from learning-path nodes and from previously created review tasks. Self-assessment is optional. Without an actual study record or self-assessment, a candidate has no proposed date. Deferral and dismissal affect recommendations. |
| Planning | Compares date preservation, priority and load balancing across 1–7 days. Uses current task duration, priority, deadline, active goal date, conservative path order, occupied plan items and user-locked times. Returns individual omissions and reasons. |

User identity comes from the server session. No Agent tool accepts an owner ID. V7 adds scoped expiring proposals with a SHA-256 digest and a durable decision receipt. V8 adds path-node feedback and a unique review-cycle key. Confirmation serializes against other decisions for the same user, verifies current tasks and reserved times, and saves all planned days in the same transaction using the existing plan service. A repeated identical decision returns its committed result. A stale preview or contradictory replay is rejected. Review creation separately deduplicates the node and review cycle.

Existing confirmed plans stay intact, including their occupied slots. Adaptive confirmation inserts plans for available tasks and keeps their task-level planned date and deadline. Completing a plan item still uses the normal plan/record workflow. An already-overdue task can be assigned a future catch-up slot. An upcoming deadline is a hard constraint. The engine schedules a full task duration when possible and reports a task it cannot fit.

## Workflow and browser

Open **学习洞察** for trends, evidence, a planning comparison or review. In the assistant **工作流** tab, try:

- 分析我最近两周的学习问题。
- 我有逾期任务，帮我重新安排。
- 给我安排下周计划，我每天有两小时。
- 我有哪些知识需要复习？
- 分析我最近两周的学习问题，安排下周计划并给我复习建议。

The workflow uses deterministic intent routing. Missing daily time stops at a persisted clarification step, with an optional subject selection. Planning stops at a strategy preview and approval. A combined request presents reviews as advice. Review tasks require a separate deliberate selection and approval. The Agent also exposes six read-only learning tools and separate plan/review proposal tools under the C-8 allowlist.

Adaptive workflow advancement runs at most 16 steps and checks its wall-clock budget between steps. Database locking and revision checks remain in effect. Paused, failed, waiting, cancelled and timed-out workflows do not advance automatically. The browser reads authenticated SSE snapshots by revision and falls back to the workflow GET endpoint after connection loss. The stream publishes only status. It uses a bounded pool of eight connections and 12-second polling windows, so it is a best-effort live status stream. Intermediate revisions can be coalesced. Business results and approvals are recovered from the saved snapshot.

The hourly scheduled cleanup removes at most 100 rows per table per pass. Retention defaults to 30 days through `app.agent.audit-retention-days` and the schedule can be adjusted with `app.agent.cleanup-delay-ms`. The cutoff applies only after expiry or receipt commit. A session with retained workflows survives cleanup. Unfinished proposals within the retention window remain available under their original validity rules.

## Offline evaluation

Run `mvn -f backend/pom.xml -Dtest=AdaptivePlanningEvaluationTest test`. The evaluation uses fixed dates, two development cases and four final cases in `backend/src/test/resources/adaptive/planning-v1.json`. It compares the existing Java rule generator, applied once per day with the same budget and candidate data, with all three adaptive schemes. Its mock mapper supplies deterministic candidate order. The baseline draft algorithm has no input for locked or already occupied times. The comparison detects collisions in drafts and does not measure what the existing plan confirmation would accept. See `backend/target/adaptive-planning-v1.json` after running it for all case-level scores.

| Four frozen final scenarios | C-8 rule per day | C-9 Minimal | C-9 Priority | C-9 Balanced |
| --- | ---: | ---: | ---: | ---: |
| Assigned slots | 9 | 8 | 8 | 8 |
| Full-duration slots | 7 | 8 | 8 | 8 |
| High-priority tasks omitted | 0 | 2 | 2 | 2 |
| Slots overlapping occupied or locked time | 1 | 0 | 0 | 0 |
| Days exceeding capacity after existing work | 1 | 0 | 0 | 0 |
| Scheduled tasks moved away from their initial date | 2 | 2 | 6 | 6 |

The overdue fixture rejects a task whose deadline leaves 15 minutes for 30 minutes of work. The oversized fixture declines a 90-minute task in a 30-minute day, where the existing algorithm can schedule a partial slot. Priority and Balanced reorder more items than Minimal in the competing-subject fixture. These are simulated planning results, and the fixture count is too small to estimate population-level effectiveness.

## Integration and opt-in provider

`node tests/c8-e2e.mjs` starts the actual packaged Spring Boot app and built Vue site over HTTP for the C-8 and C-9 scenarios. Use a disposable local MySQL 8.4 schema ending in `_test`, Redis 7, Node 22 and JDK 17. Build with `mvn -f backend/pom.xml package -DskipTests` and `npm --prefix frontend ci` followed by `npm --prefix frontend run build`. Install Playwright with `npm --prefix frontend install --no-save --package-lock=false playwright@1.58.2` and run `cd frontend && npx playwright install chromium` before returning to the repo root. Export `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, and `REDIS_PORT` for the isolated services. The CI job prepares the same dependencies without a real provider.

Explicitly set `C8_LIVE_DEEPSEEK=true` and inject `DEEPSEEK_API_KEY` through the shell environment before running the same test outside CI. The runner proxies only to DeepSeek in that mode and reports returned model, temperature, token cap, token use when available, tool names, upstream status and latency. Credentials and prompt contents are excluded from reports. The paid mode requires an isolated test database and its own validation. CI substitutes only the provider HTTP protocol. The deterministic C-9 workflows themselves do not use a model.

## Limits

The profile shows recorded activity rather than engagement inferred by a model. Time-window metrics are not an event history of date or status edits. Review needs a linked path node and evidence. It does not infer knowledge mastery from task titles. The planner scans up to 200 candidates and considers seven days at most. Neither availability outside entered constraints nor edits to existing confirmed plans are inferred. The ordered path predecessor rule can be conservative for independent learning nodes. C-9's current replay report does not calculate population rates for review recurrence, approval, or live tool selection. The database integration scenarios exercise selected guarantees with named assertions. The optional live C-9 tool-selection accuracy has no measured result.
