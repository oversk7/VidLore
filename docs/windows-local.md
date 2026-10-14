# Windows 本地运行

本机使用原生 Windows 进程，无需 Docker。配置保存在项目根目录 `.env`，本地数据和服务日志保存在 `.local/`，两者均已加入忽略规则。

## 启动

在 `D:\Projects\VidLore` 打开 PowerShell，先运行：

```powershell
pwsh -File .\scripts\start-services.ps1
```

再分别在两个终端运行：

```powershell
pwsh -File .\scripts\start-backend.ps1
```

```powershell
pwsh -File .\scripts\start-frontend.ps1
```

- 前端：<http://127.0.0.1:5173/>
- 后端健康检查：<http://127.0.0.1:9090/health>
- MinIO 控制台：<http://127.0.0.1:9001/>，账号和密码见 `.env` 中的 `MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`。
- 示例工作台：<http://127.0.0.1:5173/?demo>

## 本机服务配置

| 组件 | 配置 |
| --- | --- |
| Java | Microsoft OpenJDK 21；路径见 `.env` 的 `JAVA_HOME` |
| MySQL | 复用本机 MySQL 8.4 程序，独立实例监听 `127.0.0.1:3307`，数据在 `.local/mysql-data` |
| Redis | 使用本机已有 Memurai 服务 `127.0.0.1:6379`，逻辑库 5 |
| MinIO | `.local/bin/minio.exe`，数据在 `.local/minio-data` |
| RocketMQ | 5.3.4，NameServer 9876，Broker 10911；数据在 `.local/rocketmq-store` |
| FFmpeg / OCR | 已安装，Tesseract 包含 `chi_sim` 和 `eng` |
| 在线视频下载 | 已安装 yt-dlp，路径见 `.env` |

原有 MySQL 服务仍使用 3306，未修改原有数据、账号或密码。Memurai 由 Windows 服务管理，重启电脑后自动启动；若手动停用，可通过 `Start-Service Memurai` 恢复。其无密码连接使用 `.local/redisson.yaml` 显式配置，由 `.env` 的 `SPRING_REDIS_REDISSON_FILE` 加载，避免客户端发送空密码 AUTH。

## AI 能力与限制

当前按用户选择拆分服务：文本分析使用 DeepSeek 官方 `deepseek-flash`（[官方文档](https://api-docs.deepseek.com/zh-cn/quick_start/pricing) 对应 DeepSeek-V4.1-Flash），语音识别使用硅基流动 `TeleAI/TeleSpeechASR`。LLM 和 ASR 使用独立地址与密钥。硅基流动仅用于语音，`EMBEDDING_ENABLED=false` 关闭远程向量调用；Qdrant 同样关闭，检索继续使用关键词及已有本地缓存。未生成向量的新视频不具备向量语义召回。

若以后需要向量服务，可独立设置 `EMBEDDING_ENABLED=true`、`EMBEDDING_API_KEY`、`EMBEDDING_BASE_URL`、`EMBEDDING_MODEL`。这些设置不会改变 LLM 或 ASR 的提供方。

模型配置如下（密钥只保存在本地 `.env`）：

```dotenv
LLM_API_KEY=填写DeepSeek官方密钥
LLM_BASE_URL=https://api.deepseek.com/v1
LLM_MODEL=deepseek-flash
ASR_API_KEY=填写硅基流动密钥
ASR_URL=https://api.siliconflow.cn/v1/audio/transcriptions
ASR_MODEL=TeleAI/TeleSpeechASR
EMBEDDING_ENABLED=false
QDRANT_ENABLED=false
```

本机 `AGENT_MAX_DURATION_MS=600000`，允许长视频的摘要、规划与最终分析最多使用 10 分钟的 Agent 时间预算；原默认 120 秒不足以完成已测试的三十多分钟视频分析。本机同时设定 `AGENT_MAX_ESTIMATED_TOKENS=150000`，容纳长视频的两轮证据检索和结果校验；原默认 50000 曾在第二轮生成结果后触发预算终止。轮次上限仍为 2，模型单次请求超时仍为 `LLM_TIMEOUT_SECONDS=300`。

修改配置后重启后端。不要提交 `.env` 或在截图、日志中展示其中的密钥。

## 配置拆分与兼容性

LLM、ASR、Embedding 三类模型服务可以分别配置地址与密钥；未单独配置时仍回退到统一的 `SILICONFLOW_*` 配置。

| 文件 | 说明 |
| --- | --- |
| `server/src/main/resources/application.properties` | 增加 LLM、ASR、Embedding 的独立配置入口；兼容统一的 `SILICONFLOW_*` 配置 |
| `server/src/main/java/com/example/server/utils/AliyunAsrUtils.java` | 语音客户端改为读取自己的 API Key |
| `server/src/main/java/com/example/server/utils/EmbeddingUtils.java` | 读取独立配置；增加关闭远程向量调用的开关 |
| `server/src/main/java/com/example/server/service/VideoContextService.java` | `-vsync vfr` 改为 `-fps_mode vfr`，兼容 FFmpeg 9 |

对应有 3 项配置回归测试；Windows 启动脚本位于 `scripts/`。

## 验证

```powershell
Invoke-RestMethod http://127.0.0.1:9090/health
Invoke-RestMethod http://127.0.0.1:5173/health
pwsh -File .\scripts\check-local.ps1
```

后端应返回 `code=0`、`data=UP`。前端代理的 `/health` 也应返回相同结果。
