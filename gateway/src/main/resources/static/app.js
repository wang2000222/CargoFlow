let token = "",
  actor = {},
  view = "overview",
  orders = [],
  invoices = [],
  system = { services: [] };
const $ = (s) => document.querySelector(s),
  esc = (v) =>
    String(v ?? "").replace(
      /[&<>"']/g,
      (c) =>
        ({
          "&": "&amp;",
          "<": "&lt;",
          ">": "&gt;",
          '"': "&quot;",
          "'": "&#39;",
        })[c],
    );
const states = {
  PENDING_DISPATCH: "待调度",
  ASSIGNED: "已派车",
  IN_TRANSIT: "运输中",
  DELIVERED: "待签收",
  SIGNED: "已签收",
  CANCELLED: "已取消",
  PENDING: "待投递",
  SENT: "已投递",
  FAILED: "待重试",
  PAID: "已支付",
  OPEN: "待支付",
  ACTIVE: "运输任务",
  RELEASED: "已释放",
};
const badge = (s) =>
  `<span class="badge ${["PENDING_DISPATCH", "PENDING", "OPEN"].includes(s) ? "pending" : ["FAILED", "CANCELLED", "DOWN"].includes(s) ? "bad" : ""}">${esc(states[s] || s)}</span>`;
const money = (n) => "¥ " + (Number(n || 0) / 100).toFixed(2),
  short = (id) => esc(String(id).slice(0, 8)),
  staff = () => ["OWNER", "DISPATCHER"].includes(actor.role),
  customer = () => ["OWNER", "CUSTOMER"].includes(actor.role),
  finance = () => ["OWNER", "CUSTOMER", "FINANCE"].includes(actor.role);
async function api(path, method = "GET", body) {
  const r = await fetch("/api" + path, {
    method,
    headers: {
      Authorization: "Bearer " + token,
      "Content-Type": "application/json",
    },
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
  });
  const data = await r.json();
  if (!r.ok) throw Error(data.message || "请求失败");
  return data;
}
function toast(text) {
  $("#toast").textContent = text;
  $("#toast").style.display = "block";
  setTimeout(() => ($("#toast").style.display = "none"), 4500);
}
async function login() {
  try {
    const r = await api("/demo/login", "POST", {
      account: $("#account").value,
    });
    token = r.token;
    actor = r.actor;
    await load();
  } catch (e) {
    toast(e.message);
  }
}
async function load() {
  try {
    orders = await api("/shipments");
    system = await api("/system");
    invoices = finance() ? await api("/invoices") : [];
    await render();
  } catch (e) {
    toast(e.message);
  }
}
function ordersTable(limit) {
  const data = limit ? orders.slice(0, limit) : orders;
  return `<div class="tablewrap"><table><thead><tr><th>运单 / 线路</th><th>货物</th><th>费用</th><th>状态</th><th>操作</th></tr></thead><tbody>${data.map((o) => `<tr><td><div class="route">${esc(o.origin)} → ${esc(o.destination)}</div><div class="id">CF-${short(o.id)} · v${o.state_version}</div></td><td>${esc(o.cargo)}<br><span class="muted">${o.weight_kg} kg / ${o.distance_km} km</span></td><td>${money(o.fee_cents)}</td><td>${badge(o.state)}</td><td><button data-track="${o.id}">轨迹</button>${staff() && o.state === "ASSIGNED" ? `<button data-action="pickup" data-id="${o.id}">开始运输</button>` : ""}${staff() && o.state === "IN_TRANSIT" ? `<button data-action="deliver" data-id="${o.id}">确认送达</button>` : ""}${customer() && o.state === "DELIVERED" ? `<button data-action="sign" data-id="${o.id}">签收</button>` : ""}${customer() && ["PENDING_DISPATCH", "ASSIGNED"].includes(o.state) ? `<button data-action="cancel" data-id="${o.id}">取消</button>` : ""}</td></tr>`).join("")}</tbody></table>${data.length ? "" : '<div class="empty">暂无运单，创建一笔运输委托开始演示。</div>'}</div>`;
}
const card = (title, body, action = "") =>
  `<div class="card"><div class="sectionhead"><h2>${title}</h2>${action}</div>${body}</div>`;
async function render() {
  const titles = {
    overview: "运营总览",
    shipments: "运单管理",
    fleet: "车辆调度",
    billing: "费用结算",
    events: "事件投递",
    architecture: "微服务架构",
  };
  $("#title").textContent = titles[view];
  document
    .querySelectorAll("nav button")
    .forEach((b) => b.classList.toggle("active", b.dataset.view === view));
  let html = "";
  const create = customer()
    ? '<button class="primary" id="newOrder">＋ 创建运单</button>'
    : "";
  if (view === "overview") {
    html = `<div class="hero"><small>CONNECTED LOGISTICS</small><h2>让每一笔运输，都有迹可循。</h2><p>从运输委托、自动调度到签收结算，以独立服务协同履约。当前身份：${esc(actor.user)} / ${esc(actor.tenant)}</p><div class="lane">◉ ─── ↗</div></div><div class="metrics">${[
      ["运单总数", orders.length],
      [
        "正在履约",
        orders.filter((o) =>
          ["ASSIGNED", "IN_TRANSIT", "DELIVERED"].includes(o.state),
        ).length,
      ],
      ["已签收", orders.filter((o) => o.state === "SIGNED").length],
      [
        "账单金额",
        money(invoices.reduce((n, i) => n + Number(i.amount_cents), 0)),
      ],
    ]
      .map(
        ([t, n]) =>
          `<div class="card metric"><small>${t}</small><div class="number">${n}</div><span class="muted">当前身份可见数据</span></div>`,
      )
      .join(
        "",
      )}</div><div class="grid">${card("最近运单", ordersTable(5), create)}${card("服务运行状态", `<div class="services">${system.services.map((s) => `<div class="service"><span>${esc(s.name)}</span>${badge(s.status)}</div>`).join("")}</div><p class="muted">Gateway → OpenFeign → 独立业务服务<br>调用标识：${short(system.traceId)}</p>`)}</div>`;
  }
  if (view === "shipments")
    html = card("运输委托与履约", ordersTable(), create);
  if (view === "fleet") {
    if (!staff())
      html = card(
        "车辆调度",
        '<p class="muted">请切换至负责人或调度员查看车辆和运力。</p>',
      );
    else {
      let vehicles = await api("/vehicles"),
        assignments = await api("/assignments");
      html =
        card(
          "车辆与可用运力",
          `<table><thead><tr><th>车牌</th><th>司机</th><th>载重</th><th>已占用</th><th>可用</th></tr></thead><tbody>${vehicles.map((v) => `<tr><td>${esc(v.plate)}</td><td>${esc(v.driver)}</td><td>${v.capacity_kg} kg</td><td>${v.used_kg} kg</td><td>${v.capacity_kg - v.used_kg} kg</td></tr>`).join("")}</tbody></table>`,
          '<button id="addVehicle">＋ 添加车辆</button>',
        ) +
        "<br>" +
        card(
          "调度任务",
          `<table><thead><tr><th>运单</th><th>车辆</th><th>货重</th><th>状态</th></tr></thead><tbody>${assignments.map((a) => `<tr><td>${short(a.shipment_id)}</td><td>${short(a.vehicle_id)}</td><td>${a.weight_kg} kg</td><td>${badge(a.state)}</td></tr>`).join("")}</tbody></table>`,
        );
    }
  }
  if (view === "billing")
    html = card(
      "签收账单",
      finance()
        ? `<table><thead><tr><th>账单</th><th>运单</th><th>金额</th><th>状态</th><th>操作</th></tr></thead><tbody>${invoices.map((i) => `<tr><td>${short(i.id)}</td><td>${short(i.shipment_id)}</td><td>${money(i.amount_cents)}</td><td>${badge(i.state)}</td><td>${i.state !== "PAID" ? `<button data-pay="${i.id}">模拟支付</button>` : "支付已确认"}</td></tr>`).join("")}</tbody></table><p class="muted">签收后异步生成账单。支付为模拟流程，重复请求不会重复记录收款。</p>`
        : '<p class="muted">请切换至客户、财务或负责人查看结算。</p>',
    );
  if (view === "events") {
    if (!staff())
      html = card(
        "可靠事件投递",
        '<p class="muted">负责人和调度员可查看、重试待处理事件。</p>',
      );
    else {
      const events = await api("/outbox");
      html = card(
        "事务 Outbox",
        `<p class="muted">业务变更与待投递事件在同一事务保存；失败保留并重试。SENT 在 HTTP 模式表示业务服务已响应，在 MQ 模式表示 Broker 已确认，消费失败需检查死信队列。</p><div class="tablewrap"><table><thead><tr><th>事件 / 运单</th><th>目标</th><th>状态</th><th>次数</th><th>Trace</th><th>操作</th></tr></thead><tbody>${events.map((e) => `<tr><td>${short(e.id)} / ${short(e.shipment_id)}</td><td>${esc(e.target)}</td><td>${badge(e.state)}</td><td>${e.attempts}</td><td class="id">${esc(e.trace_id)}</td><td>${e.state !== "SENT" ? `<button data-retry="${e.id}">重新投递</button>` : "—"}</td></tr>`).join("")}</tbody></table></div>`,
      );
    }
  }
  if (view === "architecture")
    html = card(
      "技术如何服务于物流业务",
      `<div class="flow"><span>客户 / 运营工作台</span>→<span>Spring Cloud Gateway</span>→<span>运单 · 调度 · 轨迹 · 结算</span></div><div class="techgrid">${[
        ["独立服务", "五个应用、四个业务数据库，分别启动与部署。"],
        ["OpenFeign + Resilience4j", "签名调用、超时与熔断，隔离下游故障。"],
        ["事务 Outbox", "数据库事件记录、失败退避重试，业务消费者去重。"],
        ["Nacos", "可选注册发现与配置中心，需启动基础设施后验收。"],
        ["RabbitMQ", "可选持久化队列、发布确认与死信队列，需集成验收。"],
        ["Redis", "可选版本化轨迹缓存，故障回源，需集成验收。"],
      ]
        .map(
          ([t, d]) =>
            `<div class="card"><strong>${t}</strong><p class="muted">${d}</p></div>`,
        )
        .join(
          "",
        )}</div><p class="muted">默认本地模式通过可靠 HTTP 投递运行。中间件集成与真实物流供应商接口需单独验证。</p>`,
    );
  $("#content").innerHTML = html;
  bind();
}
function bind() {
  const b = $("#newOrder");
  if (b) b.onclick = () => $("#modal").showModal();
  document.querySelectorAll("[data-action]").forEach(
    (b) =>
      (b.onclick = async () => {
        try {
          let body = {};
          if (b.dataset.action === "sign") {
            const signature = prompt("请输入签收人姓名");
            if (!signature) return;
            body = { signature };
          }
          if (b.dataset.action === "cancel" && !confirm("确认取消此运单？"))
            return;
          b.disabled = true;
          await api(
            "/shipments/" + b.dataset.id + "/" + b.dataset.action,
            "POST",
            body,
          );
          toast("操作成功，关联事件正在投递");
          await load();
        } catch (e) {
          toast(e.message);
          b.disabled = false;
        }
      }),
  );
  document.querySelectorAll("[data-track]").forEach(
    (b) =>
      (b.onclick = async () => {
        try {
          const events = await api("/tracking/" + b.dataset.track);
          $("#content").innerHTML = card(
            "运单轨迹",
            `<button id="back">返回列表</button><div class="timeline">${events.map((e) => `<p><strong>${esc(states[e.state] || e.state)}</strong><br><span class="muted">${new Date(e.received_at).toLocaleString()} · 版本 ${e.state_version}</span><br><span class="id">${esc(e.trace_id)}</span></p>`).join("")}</div>`,
          );
          $("#back").onclick = render;
        } catch (e) {
          toast(e.message);
        }
      }),
  );
  document.querySelectorAll("[data-pay]").forEach(
    (b) =>
      (b.onclick = async () => {
        try {
          await api("/invoices/" + b.dataset.pay + "/pay", "POST", {});
          toast("模拟支付已确认");
          await load();
        } catch (e) {
          toast(e.message);
        }
      }),
  );
  document.querySelectorAll("[data-retry]").forEach(
    (b) =>
      (b.onclick = async () => {
        try {
          await api("/outbox/" + b.dataset.retry + "/retry", "POST", {});
          toast("已进入重试队列");
          await load();
        } catch (e) {
          toast(e.message);
        }
      }),
  );
  const v = $("#addVehicle");
  if (v)
    v.onclick = async () => {
      const plate = prompt("车牌号");
      if (!plate) return;
      const driver = prompt("司机姓名");
      if (!driver) return;
      const capacityKg = Number(prompt("车辆载重 / kg", "5000"));
      try {
        await api("/vehicles", "POST", { plate, driver, capacityKg });
        await load();
      } catch (e) {
        toast(e.message);
      }
    };
}
$("#account").onchange = login;
$("#refresh").onclick = load;
$("#closeModal").onclick = () => $("#modal").close();
document.querySelectorAll("nav button").forEach(
  (b) =>
    (b.onclick = () => {
      view = b.dataset.view;
      render().catch((e) => toast(e.message));
    }),
);
$("#orderForm").onsubmit = async (e) => {
  e.preventDefault();
  const data = Object.fromEntries(new FormData(e.target));
  data.weightKg = Number(data.weightKg);
  data.distanceKm = Number(data.distanceKm);
  data.requestKey = crypto.randomUUID();
  try {
    await api("/shipments", "POST", data);
    $("#modal").close();
    toast("运单已创建，等待调度");
    view = "shipments";
    await load();
  } catch (error) {
    toast(error.message);
  }
};
login();
