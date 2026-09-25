const zh = {
  "document.title": "Octopus — 连接、观察并控制每一台设备",
  "meta.description": "Octopus 是一个开源 IoT 控制平面，用于连接、观察和运营设备舰队。",
  skip: "跳到正文",
  "nav.label": "主导航",
  "nav.platform": "平台",
  "nav.capabilities": "能力",
  "nav.architecture": "架构",
  "nav.developers": "开发者",
  "nav.github": "前往 GitHub",
  "locale.label": "语言",
  "hero.eyebrow": "开源 IOT 控制平面",
  "hero.title": "连接每台设备。<br><span>看见每个信号。</span><br>放心采取行动。",
  "hero.intro": "在一个默认保证租户安全的平台中，完成设备身份签发、有序遥测接入、设备状态管理、命令下发与告警响应。",
  "hero.start": "本地运行 Octopus",
  "hero.explore": "了解平台能力",
  "hero.guarantees": "平台保证",
  "hero.assurance.identity": "证书绑定的设备身份",
  "hero.assurance.delivery": "持久且有序的数据交付",
  "hero.assurance.tenant": "默认启用租户隔离",
  "console.label": "交互式 IoT 运维控制台",
  "console.site": "北区工厂",
  "console.live": "实时",
  "console.command": "下发命令",
  "console.nav": "控制台导航",
  "console.demo": "演示设备舰队",
  "console.overview": "运行态总览",
  "console.connected": "已连接",
  "console.throughput": "每分钟消息",
  "console.alerts": "待处理告警",
  "console.review": "需复核",
  "console.power": "实时功率",
  "console.lastHour": "最近 60 分钟",
  "console.chart": "演示用实时遥测趋势图",
  "console.demand": "实时负荷",
  "console.streaming": "持续接收中",
  "console.devices": "设备",
  "console.allDevices": "全部设备 →",
  "console.pump": "冷却回路",
  "console.meter": "主进线",
  "console.valve": "取水入口",
  "console.online": "在线",
  "console.attention": "需关注",
  "console.alarmTitle": "压力超出运行区间",
  "console.alarmMeta": "阀门 W-033 · 4 分钟前触发",
  "console.acknowledge": "确认告警",
  "console.acknowledged": "已确认",
  "console.note": "基于 v1.0 能力构建的交互式产品概念，设备数据为演示值。",
  "console.commandToast": "命令已进入可靠下发队列",
  "console.alarmToast": "告警已确认并写入审计轨迹",
  "protocol.label": "平台技术边界",
  "platform.kicker": "设备运营闭环",
  "platform.title": "从设备接入<br>到业务决策。",
  "platform.intro": "Octopus 为产品和运维团队提供从安全接入到实时行动的一条连续链路。",
  "loop.connect.title": "连接",
  "loop.connect.body": "认领硬件、签发运行证书，并把设备授权到精确的 MQTT 主题空间。",
  "loop.observe.title": "观察",
  "loop.observe.body": "接收有序遥测、归一化测量值、跟踪在线状态，并查询受约束的时序数据。",
  "loop.understand.title": "理解",
  "loop.understand.body": "把测量数据变成仪表盘、有状态告警、事件与租户安全的运维上下文。",
  "loop.act.title": "行动",
  "loop.act.body": "可靠地下发命令和期望状态变更，并保留可追踪、可安全重放的结果。",
  "capabilities.kicker": "一个平台 / 八条触手",
  "capabilities.title": "设备舰队很复杂。<br>控制平面不该复杂。",
  "capabilities.intro": "选择一项能力，查看 Octopus 负责什么，以及它在真实运维中为何重要。",
  "capabilities.label": "Octopus 平台能力",
  "cap.identity": "设备身份",
  "cap.connectivity": "连接管理",
  "cap.telemetry": "遥测处理",
  "cap.shadow": "设备状态",
  "cap.commands": "远程命令",
  "cap.alarms": "告警事件",
  "cap.analytics": "分析看板",
  "cap.tenancy": "多租户",
  "capabilities.input": "输入",
  "capabilities.output": "输出",
  "capabilities.guarantee": "保证",
  "capabilities.read": "阅读设计说明 ↗",
  "architecture.kicker": "端到端数据链路",
  "architecture.title": "为真实设备舰队中<br>那些麻烦而生。",
  "architecture.intro": "网络中断、重复消息、事件重放、延迟读数和租户边界都是日常运行条件，不是可以忽略的边缘情况。",
  "architecture.flow": "设备数据处理链路",
  "path.device": "设备",
  "path.deviceNote": "证书 + MQTT",
  "path.ingress": "安全接入",
  "path.ingressNote": "校验 + 确认",
  "path.kafka": "有序事件",
  "path.kafkaNote": "分区 + 重放",
  "path.process": "数据处理",
  "path.processNote": "去重 + 归一化",
  "path.action": "观察与行动",
  "path.actionNote": "查询 + 告警 + 命令",
  "proof.noSilent": "不允许静默丢失",
  "proof.noSilentBody": "MQTT 只有在数据持久交接后才确认。失败记录保持可重放，或进入显式隔离路径。",
  "proof.noCrossTenant": "不允许跨租户捷径",
  "proof.noCrossTenantBody": "JWT、应用上下文、PostgreSQL FORCE RLS、Kafka 消息、Redis 键和对象路径都会携带租户身份。",
  "proof.noMagic": "不依赖基础设施魔法",
  "proof.noMagicBody": "纯 Java 领域边界位于可替换的 MQTT、AWS IoT、存储和分析适配器之后。",
  "developers.kicker": "从真实系统开始",
  "developers.title": "克隆。<br>运行。<br>检查每个边界。",
  "developers.intro": "Octopus 是一个 Java 21 多模块平台，本地基础设施、Kubernetes 清单、架构决策与测试都在同一仓库中。",
  "developers.modules": "模块",
  "developers.tests": "测试",
  "developers.baseline": "技术基线",
  "developers.copy": "复制",
  "developers.copied": "已复制",
  "developers.step1": "验证所有模块",
  "developers.step2": "启动数据与消息服务",
  "developers.step3": "渲染部署基线",
  "final.kicker": "你的设备已经在说话",
  "final.title": "给它们一个<br>被真正理解的地方。",
  "final.github": "在 GitHub 探索 Octopus ↗",
  "final.docs": "阅读架构文档",
  "footer.statement": "一个让设备舰队可归属、可观察、可运营的开源控制平面。",
  "footer.source": "源码",
  "footer.changelog": "变更记录",
};

const capabilityContent = {
  en: {
    identity: { index: "ARM 01", status: "TRUST", title: "Give every device a verifiable identity.", description: "Claim hardware once, bind it to a tenant, and issue a short-lived operational certificate that becomes the authority for every connection.", input: "Factory identity + CSR", output: "Tenant-bound certificate", guarantee: "One identity, one tenant, one device", link: "https://github.com/fishered/octopus/blob/master/docs/device-certificate-lifecycle.md" },
    connectivity: { index: "ARM 02", status: "CONNECT", title: "Meet devices where they already are.", description: "Keep MQTT and AWS IoT behind provider-neutral ports so fleets can connect without leaking broker semantics into the domain.", input: "MQTT / cloud-provider traffic", output: "Normalized device envelopes", guarantee: "Provider changes do not rewrite device identity", link: "https://github.com/fishered/octopus/blob/master/docs/iot-plugin-architecture.md" },
    telemetry: { index: "ARM 03", status: "ORDER", title: "Turn noisy messages into dependable telemetry.", description: "Validate, deduplicate, order, normalize, meter, and persist device readings while keeping the original stream available for replay.", input: "Raw device measurements", output: "Queryable normalized time series", guarantee: "Durable handoff before acknowledgement", link: "https://github.com/fishered/octopus/blob/master/docs/telemetry-pipeline.md" },
    shadow: { index: "ARM 04", status: "STATE", title: "Know what a device is—and what it should become.", description: "Track live presence, reported state, desired state, monotonic versions, and governed writable fields without pretending an offline device is reachable.", input: "Reported + desired state", output: "Versioned device projection", guarantee: "Offline-safe state synchronization", link: "https://github.com/fishered/octopus/blob/master/docs/device-presence-and-shadow.md" },
    commands: { index: "ARM 05", status: "ACT", title: "Send commands without losing the outcome.", description: "Authorize operator intent, write it durably, preserve per-device ordering, dispatch through the active provider, and project every status transition.", input: "Tenant-scoped operator command", output: "Traceable command lifecycle", guarantee: "Replay-safe, idempotent status handling", link: "https://github.com/fishered/octopus/blob/master/docs/device-command-lifecycle.md" },
    alarms: { index: "ARM 06", status: "RESPOND", title: "Make abnormal conditions actionable.", description: "Evaluate normalized telemetry against tenant-owned rules and maintain an incident lifecycle that late or replayed data cannot roll backward.", input: "Telemetry + alarm rules", output: "Open, acknowledged, resolved incidents", guarantee: "Idempotent evaluation ledger", link: "https://github.com/fishered/octopus/blob/master/docs/alarm-pipeline.md" },
    analytics: { index: "ARM 07", status: "SEE", title: "Build operational views without exposing the database.", description: "Query bounded time windows, apply shared meter semantics, and compose tenant-owned dashboards whose widgets cannot bypass authorization.", input: "Meter-scoped analytic queries", output: "Series + dashboard aggregates", guarantee: "Bounded, authorized time-series access", link: "https://github.com/fishered/octopus/blob/master/docs/analytics-and-dashboards.md" },
    tenancy: { index: "ARM 08", status: "ISOLATE", title: "Keep every customer inside their own boundary.", description: "Reinforce tenant scope through human sessions, resource authorization, application services, database RLS, caches, events, and object keys.", input: "Verified human or device identity", output: "Tenant-scoped operation", guarantee: "Deny by default at every layer", link: "https://github.com/fishered/octopus/blob/master/docs/security-and-tenancy.md" },
  },
  "zh-CN": {
    identity: { index: "触手 01", status: "可信", title: "让每台设备都拥有可验证的身份。", description: "一次认领硬件，将它绑定到租户，并签发短周期运行证书，让证书成为每次连接的真实权限来源。", input: "出厂身份 + CSR", output: "租户绑定的运行证书", guarantee: "一个身份、一个租户、一台设备" },
    connectivity: { index: "触手 02", status: "连接", title: "用设备已经采用的方式连接它。", description: "把 MQTT 与 AWS IoT 隔离在供应商中立的端口之后，避免 Broker 语义渗入领域模型。", input: "MQTT / 云服务商消息", output: "归一化设备消息", guarantee: "更换供应商无需重写设备身份" },
    telemetry: { index: "触手 03", status: "有序", title: "把嘈杂消息变成可靠遥测。", description: "校验、去重、排序、归一化、计量并持久化设备读数，同时保留可重放的原始事件流。", input: "原始设备测量值", output: "可查询的归一化时序", guarantee: "持久交接后才发送确认" },
    shadow: { index: "触手 04", status: "状态", title: "知道设备现在如何，也知道它应该如何。", description: "跟踪在线状态、上报状态、期望状态、单调版本和受控可写字段，不假装离线设备仍然可达。", input: "上报状态 + 期望状态", output: "版本化设备投影", guarantee: "对离线设备安全的状态同步" },
    commands: { index: "触手 05", status: "行动", title: "下发命令，也不丢失结果。", description: "校验操作意图、可靠写入、保持设备内顺序、通过当前供应商下发，并投影每一次状态变化。", input: "限定租户范围的操作命令", output: "可追踪命令生命周期", guarantee: "可重放且幂等的状态处理" },
    alarms: { index: "触手 06", status: "响应", title: "让异常真正触发行动。", description: "用租户自有规则评估归一化遥测，并维护不会被迟到或重放数据倒退的事件生命周期。", input: "遥测 + 告警规则", output: "打开、确认、解决的事件", guarantee: "幂等评估台账" },
    analytics: { index: "触手 07", status: "洞察", title: "构建运维视图，而不是暴露数据库。", description: "查询有界时间窗、复用统一计量语义，并组合任何组件都无法绕过授权的租户仪表盘。", input: "限定仪表范围的分析查询", output: "时序数据 + 仪表盘聚合", guarantee: "有界、已授权的时序访问" },
    tenancy: { index: "触手 08", status: "隔离", title: "让每个客户始终留在自己的边界内。", description: "通过人员会话、资源授权、应用服务、数据库 RLS、缓存、事件和对象键逐层强化租户范围。", input: "已验证的人员或设备身份", output: "租户范围内的操作", guarantee: "每一层都默认拒绝" },
  },
};

const sourceText = Object.fromEntries(
  [...document.querySelectorAll("[data-i18n]")].map((element) => [element.dataset.i18n, element.textContent]),
);
const sourceHtml = Object.fromEntries(
  [...document.querySelectorAll("[data-i18n-html]")].map((element) => [element.dataset.i18nHtml, element.innerHTML]),
);
const sourceContent = Object.fromEntries(
  [...document.querySelectorAll("[data-i18n-content]")].map((element) => [element.dataset.i18nContent, element.content]),
);
const sourceAria = Object.fromEntries(
  [...document.querySelectorAll("[data-i18n-aria-label]")].map((element) => [element.dataset.i18nAriaLabel, element.getAttribute("aria-label")]),
);
const englishTitle = document.title;

function normalizeLocale(locale) {
  return String(locale || "").toLowerCase().startsWith("zh") ? "zh-CN" : "en";
}

function readPreferredLocale() {
  try {
    return normalizeLocale(localStorage.getItem("octopus.locale") || navigator.language);
  } catch {
    return normalizeLocale(navigator.language);
  }
}

let currentLocale = readPreferredLocale();
let activeCapability = "identity";
let alarmAcknowledged = false;
let toastTimer;

const capabilityFields = {
  index: document.querySelector("#capability-index"),
  status: document.querySelector("#capability-status"),
  title: document.querySelector("#capability-title"),
  description: document.querySelector("#capability-description"),
  input: document.querySelector("#capability-input"),
  output: document.querySelector("#capability-output"),
  guarantee: document.querySelector("#capability-guarantee"),
  link: document.querySelector("#capability-link"),
};

function localCopy(key) {
  return currentLocale === "zh-CN" ? zh[key] : sourceText[key];
}

function renderCapability(key) {
  const english = capabilityContent.en[key];
  if (!english) return;
  const localized = currentLocale === "zh-CN"
    ? { ...english, ...capabilityContent["zh-CN"][key] }
    : english;

  activeCapability = key;
  document.querySelectorAll("[data-capability]").forEach((button) => {
    const selected = button.dataset.capability === key;
    button.classList.toggle("is-active", selected);
    button.setAttribute("aria-pressed", String(selected));
  });

  Object.entries(capabilityFields).forEach(([field, element]) => {
    if (field === "link") element.href = localized.link;
    else element.textContent = localized[field];
  });
}

function applyLocale(locale, { persist = true } = {}) {
  currentLocale = normalizeLocale(locale);
  const isChinese = currentLocale === "zh-CN";

  document.documentElement.lang = currentLocale;
  document.documentElement.dataset.locale = currentLocale;
  document.title = isChinese ? zh["document.title"] : englishTitle;

  document.querySelectorAll("[data-i18n]").forEach((element) => {
    const key = element.dataset.i18n;
    element.textContent = isChinese ? zh[key] : sourceText[key];
  });
  document.querySelectorAll("[data-i18n-html]").forEach((element) => {
    const key = element.dataset.i18nHtml;
    element.innerHTML = isChinese ? zh[key] : sourceHtml[key];
  });
  document.querySelectorAll("[data-i18n-content]").forEach((element) => {
    const key = element.dataset.i18nContent;
    element.content = isChinese ? zh[key] : sourceContent[key];
  });
  document.querySelectorAll("[data-i18n-aria-label]").forEach((element) => {
    const key = element.dataset.i18nAriaLabel;
    element.setAttribute("aria-label", isChinese ? zh[key] : sourceAria[key]);
  });
  document.querySelectorAll("[data-locale]").forEach((button) => {
    const selected = button.dataset.locale === currentLocale;
    button.classList.toggle("is-active", selected);
    button.setAttribute("aria-pressed", String(selected));
  });

  if (alarmAcknowledged) {
    const acknowledgeButton = document.querySelector("#ack-alarm");
    acknowledgeButton.textContent = isChinese ? zh["console.acknowledged"] : "Acknowledged";
  }
  renderCapability(activeCapability);

  if (persist) {
    try {
      localStorage.setItem("octopus.locale", currentLocale);
    } catch {
      // Language switching still works when storage is unavailable.
    }
  }
}

function showToast(message) {
  const toast = document.querySelector("#console-toast");
  window.clearTimeout(toastTimer);
  toast.textContent = message;
  toast.classList.add("is-visible");
  toastTimer = window.setTimeout(() => toast.classList.remove("is-visible"), 2400);
}

document.querySelectorAll("[data-locale]").forEach((button) => {
  button.addEventListener("click", () => applyLocale(button.dataset.locale));
});

document.querySelectorAll("[data-capability]").forEach((button) => {
  button.addEventListener("click", () => renderCapability(button.dataset.capability));
});

document.querySelector("#demo-command")?.addEventListener("click", () => {
  showToast(currentLocale === "zh-CN" ? zh["console.commandToast"] : "Command entered the durable dispatch queue");
});

document.querySelector("#ack-alarm")?.addEventListener("click", (event) => {
  alarmAcknowledged = true;
  event.currentTarget.disabled = true;
  event.currentTarget.textContent = currentLocale === "zh-CN" ? zh["console.acknowledged"] : "Acknowledged";
  showToast(currentLocale === "zh-CN" ? zh["console.alarmToast"] : "Alarm acknowledged and written to the audit trail");
});

document.querySelector("#copy-command")?.addEventListener("click", async (event) => {
  const commands = "./mvnw verify\ndocker compose up -d\nkubectl kustomize deploy/k8s/base";
  try {
    await navigator.clipboard.writeText(commands);
    event.currentTarget.textContent = currentLocale === "zh-CN" ? zh["developers.copied"] : "Copied";
    window.setTimeout(() => {
      event.currentTarget.textContent = localCopy("developers.copy");
    }, 1800);
  } catch {
    showToast(commands);
  }
});

function updateClock() {
  const clock = document.querySelector("#console-clock");
  if (!clock) return;
  clock.textContent = new Intl.DateTimeFormat("en-GB", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
    timeZone: "UTC",
  }).format(new Date());
}

applyLocale(currentLocale, { persist: false });
updateClock();
const clockTimer = window.setInterval(updateClock, 1000);
window.addEventListener("pagehide", () => {
  window.clearInterval(clockTimer);
  window.clearTimeout(toastTimer);
});
