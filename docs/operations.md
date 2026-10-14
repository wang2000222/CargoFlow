# 运行、中间件与生产差距 / Operations

## 默认模式

`CF_NACOS=false`, `CF_MQ=false`, `CF_REDIS=false`, `CF_DEMO=true`。五个独立进程，业务服务各自使用本地 H2 文件；可靠事件通过签名 HTTP / OpenFeign 投递。数据库只由所属服务访问。

| Environment                                              | Meaning                                   |
| -------------------------------------------------------- | ----------------------------------------- |
| CF_SECRET                                                | 共享签名密钥，至少 32 字符；不要提交      |
| CF_DEMO                                                  | 演示登录、车辆种子与模拟支付开关          |
| CF_JOBS                                                  | Outbox / 回调定时投递开关                 |
| SHIPMENT_URL / DISPATCH_URL / TRACKING_URL / BILLING_URL | 服务地址，默认本地；支持 service://服务名 |
| DB_URL / DB_USERNAME / DB_PASSWORD                       | 各服务数据库配置，必须分别设置            |
| SERVER_ADDRESS / SERVER_PORT                             | 默认仅本地绑定                            |
| CF_NACOS / NACOS_ADDR                                    | Nacos 发现与配置开关 / 地址               |
| CF_MQ / RABBIT_HOST / RABBIT_USER / RABBIT_PASSWORD      | 消息模式与 Broker 凭据                    |
| CF_REDIS / REDIS_HOST / REDIS_PORT / REDIS_PASSWORD      | 可选缓存                                  |

`CF_DEMO=false` 时登录与模拟支付明确拒绝请求，正式身份与支付渠道没有自动替代实现。

## Windows socket workaround

本次机器的 JDK 17/21 在 Windows Unix socket 回连时报 Invalid argument。运行时可让内部 Pipe 回退到 TCP：

```powershell
$root=(Get-Location).Path
$env:JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=$root/.local/unused-unix-socket-dir"
./ops/start.ps1 -JavaHome $env:JAVA_HOME
```

该路径应不存在，不要创建它。仅对出现此错误的本机使用，不要作为所有环境的默认设置。当前本地验收使用此配置，未更改系统 Java 设置。

## RabbitMQ + Redis integration template

Docker Desktop engine 在本次验收环境未运行，以下模板**未完成容器集成验收**。需要运行引擎与镜像拉取权限。

```powershell
mvn package
# Set CF_SECRET (32+ random characters) and RABBIT_PASSWORD locally.
docker compose -f ops/compose.yml up --build -d
./ops/smoke.ps1
```

模板仅暴露本机 8100 和 Rabbit 管理端口；数据使用独立卷。镜像使用 major-version tag，生产需固定经过测试的镜像 digest。当前 H2 卷配置为单实例演示，不能横向复制同一个数据库文件。

Rabbit topology: durable direct exchange `cargoflow.events`, per-service durable queue `<service>.events`, dead exchange `cargoflow.dead`, queue `<service>.dead`. Persistent messages have signed envelopes. The publisher waits up to three seconds for a correlated confirm and rejects unroutable returns; unconfirmed publishes remain retryable.

Consumer local delivery goes through signed HTTP to its own internal endpoint, and domain constraints deduplicate repeated messages. Rejected delivery is dead-lettered (`default-requeue-rejected=false`). For a transient outage or capacity rejection in MQ mode, inspect the service's dead queue, fix capacity/service health, then use RabbitMQ management's **move** function to move messages back to the original service queue. Do not republish unsigned edited envelopes. Replay must be tested in an actual Broker before production use.

Redis timeline cache has 30-second TTL; unavailable Redis falls back to SQL. It is an optimization and not a business source of truth. Customers are authorized against the tenant-owned timeline before any cache read.

## Nacos

Use a compatible Nacos server following [official deployment instructions](https://nacos.io/en/docs/latest/quickstart/quick-start-docker/). This repository does not include a verified Nacos container cluster. Provide credentials and namespaces according to the target deployment; do not expose an unauthenticated config center.

Enable `CF_NACOS=true`, set `NACOS_ADDR`, and optionally `SPRING_PROFILES_ACTIVE=nacos` to import `<spring.application.name>.yml` from group `CARGOFLOW`. Configuration import is optional and happens at startup; current `@Value` fields do **not** promise dynamic live reload.

Set URLs to `service://shipment-service`, `service://dispatch-service`, `service://tracking-service`, `service://billing-service` for discovery-based routing. Peers resolve registered instances through DiscoveryClient; gateway uses its load-balancer route filter. Validate registration, actual chosen instances, auth and failover on your deployed Nacos environment. These integrations are compiled but not runtime-verified here.

## Recovery demonstration

Default HTTP mode: stop only dispatch-service, create a shipment, confirm PENDING_DISPATCH and failed outbox; restart dispatch-service with the same secret/database. Existing durable tasks should recover within startup and backoff. Do not delete databases as a recovery step. `ops/recovery.ps1` automates this non-production scenario and verifies its owned PID before stopping.

## Production gaps

Before paid rollout, implement and independently accept: real OIDC/SSO and staff/account lifecycle; per-service credentials/mTLS and rotation; managed relational databases and versioned migrations (current schema.sql is bootstrap-only); tenant onboarding; contract rates/tax/refunds/reconciliation; carrier and GPS adapters; verifiable POD; immutable operator audit; monitoring/tracing export and alerting; retry/dead-letter operational ownership; backups/restore drills; load, concurrency, security and HA tests. Current Trace IDs are correlation identifiers, not an OpenTelemetry deployment.

This portfolio demonstrates bounded workflows and engineering decisions, not a production readiness certification.
