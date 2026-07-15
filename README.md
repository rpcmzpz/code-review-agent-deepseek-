# Code Review Agent

基于 **DeepSeek API** 的多维度代码审查服务，SpringBoot 3 + Java 17。

## 功能

- 🔍 **多维度并行审查**：bug 检测、安全审计、性能分析，每个维度独立 prompt + 并发调用
- 🤖 **DeepSeek 驱动**：调用 DeepSeek Chat API，结构化返回问题列表
- ⚡ **异步并行**：CompletableFuture + 线程池，N 个维度 ≈ 单次 API 耗时
- 🛡 **容错**：单维度失败不影响其他维度，markdown 代码块自动剥离

## 技术栈

| 层 | 技术 |
|---|------|
| 框架 | SpringBoot 3.3.5 |
| 语言 | Java 17 |
| 构建 | Maven |
| HTTP 客户端 | RestTemplate |
| JSON | Jackson (ObjectMapper, TypeReference) |
| 并发 | CompletableFuture + ExecutorService |
| LLM | DeepSeek Chat API |

## 快速启动

```bash
# 1. 设置 API Key
export DEEPSEEK_API_KEY=sk-xxxxxxxx

# 2. 启动（本地开发自动加载 application-local.yml）
cd code-review-agent
mvn spring-boot:run

# 3. 测试
curl -X POST http://localhost:8080/api/review \
  -H "Content-Type: application/json" \
  -d '{
    "code": "def get_user(id):\n    user = db.query(id)\n    return user.name",
    "language": "python",
    "dimensions": ["bug", "security"]
  }'
```

## API

### POST /api/review

**请求体：**

```json
{
  "code": "待审查的代码",
  "language": "python",
  "dimensions": ["bug", "security", "performance"],
  "context": "可选，补充说明"
}
```

**响应体：**

```json
{
  "success": true,
  "issues": [
    {
      "severity": "ERROR",
      "category": "空指针",
      "line": 2,
      "title": "潜在空指针引用",
      "description": "db.query(id) 可能返回 None...",
      "suggestion": "在访问前检查是否为 None"
    }
  ],
  "summary": "维度1总结 | 维度2总结",
  "totalIssues": 2
}
```

**支持的维度：**

| 维度 | 关注点 |
|------|--------|
| `bug` | 逻辑错误、空指针、边界条件、异常处理 |
| `security` | 注入攻击、敏感信息泄露、权限绕过 |
| `performance` | 不必要的循环、重复查询、内存浪费 |

## 项目结构

```
src/main/java/com/zhuhai/codereview/
├── CodeReviewApplication.java        # 启动类
├── controller/
│   └── ReviewController.java         # POST /api/review + 入参校验
├── model/
│   ├── CodeReviewRequest.java        # 请求体（code, language, dimensions）
│   ├── CodeReviewResponse.java       # 响应体（success, issues, summary）
│   └── Issue.java                    # 单个问题（severity, category, line, title...）
└── service/
    ├── DeepSeekClient.java           # RestTemplate 调 DeepSeek API
    └── CodeReviewService.java        # 维度分发 → 并行调用 → 结果合并
```

## 架构

```
POST /api/review
      │
      ▼
ReviewController  ── 空值校验
      │
      ▼
CodeReviewService.review()
      │
      ├─ CompletableFuture ─ bug       ──► DeepSeekClient ──► DimResult
      ├─ CompletableFuture ─ security  ──► DeepSeekClient ──► DimResult
      └─ CompletableFuture ─ performance──► DeepSeekClient ──► DimResult
      │                                                        │
      └── 并行（exceptionally 兜底）────────────────────────────┘
                              │
                          mergeResults()
                              │
                    去重 + summary 聚合
                              │
                     CodeReviewResponse
```

## 安全

- API Key 使用环境变量或 `application-local.yml`（已 gitignore）
- 主配置文件 `application.yml` 不含真实 Key
- RestTemplate 连接超时 5s，读取超时 30s
