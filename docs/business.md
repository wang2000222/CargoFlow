# 业务边界与 API / Business and API

## 场景

物流公司管理运输委托和车辆载重，客户查询自己的订单并确认签收，调度员推进履约，财务查看与确认模拟收款。演示两个商家 tenant-a / tenant-b；演示账号是固定身份，不是完整商家开户系统。

## 状态与费用

`PENDING_DISPATCH → ASSIGNED → IN_TRANSIT → DELIVERED → SIGNED`

PENDING_DISPATCH / ASSIGNED 可取消为 CANCELLED；运输后取消需业务人工协商，本版本拒绝自助取消。签收姓名不可空。重复完成相同操作返回当前结果；不合法的状态跳转返回 409。

演示金额用整数分保存：`3000 + weightKg × 50 + distanceKm × 30`。重量 1–100000 kg，距离 1–10000 km。尚未实现合同阶梯价、体积重、税率、到付、退款、对账和发票开具，不能用演示公式报价真实运输。

车辆可同时承接多笔任务，以剩余载重约束分配；这不是包含线路、容积、司机工时和时窗约束的优化算法。容量不足的 HTTP 投递任务保留失败状态并重试，运营可添加车辆。

## 外部 API（网关 /api 前缀）

| Method     | Path                      | Role / 用途                      |
| ---------- | ------------------------- | -------------------------------- |
| POST       | `/demo/login`             | 固定演示身份，返回签名会话 token |
| GET        | `/system`                 | 登录后查看四个业务服务状态       |
| GET / POST | `/shipments`              | 订单列表 / 客户或负责人下单      |
| GET        | `/shipments/{id}`         | 商家隔离，客户仅本人             |
| POST       | `/shipments/{id}/pickup`  | 调度员 / 负责人                  |
| POST       | `/shipments/{id}/deliver` | 调度员 / 负责人                  |
| POST       | `/shipments/{id}/sign`    | 客户 / 负责人，body: signature   |
| POST       | `/shipments/{id}/cancel`  | 客户 / 负责人                    |
| GET / POST | `/vehicles`               | 调度员 / 负责人查看或添加车辆    |
| GET        | `/assignments`            | 查看当前商家任务，客户仅本人     |
| GET        | `/tracking/{id}`          | 授权后查看轨迹版本               |
| GET        | `/invoices`               | 客户本人 / 财务 / 负责人         |
| POST       | `/invoices/{id}/pay`      | 模拟支付，幂等                   |
| GET        | `/outbox`                 | 调度员 / 负责人查看事件          |
| POST       | `/outbox/{id}/retry`      | 重新排入未送达事件               |

Create body:

```json
{
  "requestKey": "unique-client-key",
  "origin": "苏州仓库",
  "destination": "上海仓库",
  "cargo": "配件",
  "weightKg": 120,
  "distanceKm": 110
}
```

Each external business request needs `Authorization: Bearer <demo-token>`. Gateway replaces principal headers with a signed tenant/user/role identity. Internal endpoints are not exposed by gateway routes. Service calls require a shared-key HMAC, a 60-second timestamp window and a fresh nonce. The demo token is a custom HMAC session, **not JWT/OIDC**. A real deployment needs an identity provider, TLS and service credential rotation.

## Consistency guarantees

Shipment changes and their outbox entries commit together. Dispatch reservations and callbacks commit together. Workers retry durable records with bounded backoff. Domain uniqueness and row locks reduce duplicate side effects; uniqueness failures may be retried after a concurrent winner commits.

Cancellation emits capacity release. If a late assignment callback reaches a cancelled order, another release is emitted. Dispatch release is idempotent and a tombstone prevents a later reservation from allocating capacity. This is application compensation, not an all-or-nothing distributed transaction.

Tracking sorts business versions instead of arrival time. Cache keys include both highest version and event count, so late lower versions do not reuse an incomplete timeline key. A duplicate version with a conflicting state is rejected.

In MQ mode, outbox `SENT` means broker acceptance. Business rejects or unavailable consumers can enter a durable dead-letter queue; operator replay after correction is required. The database-based HTTP mode and broker mode have different delivery diagnostics and must not be described as equivalent successful consumption.
