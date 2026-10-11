const zh = {
  "document.title": "Octopus — 让第一台 AIoT 设备今天上线",
  "meta.description": "Octopus 是一个开源 AIoT 基础平台，提供幂等设备接入、可插拔协议运行时和待审核的 AI 集成建议。",
  "skip": "跳到主要内容",
  "nav.label": "主导航",
  "nav.protocols": "协议",
  "nav.aiot": "AIoT",
  "nav.quickstart": "快速开始",
  "nav.github": "GitHub",
  "locale.label": "语言",
  "hero.eyebrow": "开源 AIoT 快速起步平台",
  "hero.title": "让第一台设备，<br><em>今天就上线。</em>",
  "hero.intro": "用一次幂等请求创建设备、硬件与连接器绑定，再通过统一插件运行时接入 MQTT、云平台和工业边缘协议——不用为每种设备重写整个平台。",
  "hero.start": "3 步开始",
  "hero.protocols": "查看协议地图",
  "hero.honesty": "<strong>接入成功必须可验证。</strong> 插件按稳定顺序启动，绑定与编解码器显式选择；AI 只生成待审核方案，不直接修改设备。",
  "lab.label": "交互式协议接入实验台",
  "lab.sticker": "第一台设备实验台",
  "lab.title": "选择设备说话的方式。",
  "lab.demo": "交互演示",
  "lab.tabs": "接入方式",
  "protocol.builtin": "已内置",
  "protocol.adapter": "适配器",
  "protocol.contract": "契约",
  "protocol.fieldbus": "工业协议",
  "protocol.edge": "边缘",
  "lab.device": "设备",
  "lab.stream": "事件流",
  "lab.copy": "复制",
  "lab.copied": "已复制",
  "lab.claim": "认领身份",
  "lab.connect": "建立连接",
  "lab.streamData": "开始上报",
  "lab.run": "模拟接入",
  "lab.running": "正在连接…",
  "lab.note": "基于所选 v1.0 接入路径的交互流程演示。",
  "ribbon.label": "协议支持状态摘要",
  "protocols.tag": "协议地图",
  "protocols.title": "协议可以很多。<br><em>设备语言只要一种。</em>",
  "protocols.intro": "能直连的保持直连，工业协议留在设备附近。进入平台前，每条链路都会变成带身份的统一设备消息。",
  "protocols.now": "现在可用",
  "protocols.mqtt": "第一条生产路径：持久会话、JSON 编解码、QoS 1 命令，以及遥测、命令结果与上报状态。",
  "protocols.adapter": "云厂商适配器",
  "protocols.aws": "把云厂商路由放在统一命令与消息端口之后，不让领域模型继承 Broker 的专有行为。",
  "protocols.contract": "插件契约",
  "protocols.http": "传输与编解码相互独立。增加连接器时，不需要让遥测、设备状态或命令理解线上的协议细节。",
  "protocols.pattern": "边缘扩展模式",
  "protocols.fieldbus": "原生 SDK 与串口驱动运行在边缘插件中，在设备附近归一化，再向云端发送稳定的设备消息。",
  "protocols.principle": "<strong>新增一种协议，应该只增加一个插件，而不是重写八套能力。</strong>身份、遥测、状态、命令、告警、分析、存储与租户边界保持不变。",
  "protocols.read": "阅读插件设计 ↗",
  "ai.tag": "可插拔 AI，默认只读",
  "ai.title": "让模型提出接入方案。<br><em>让平台决定它是否有效。</em>",
  "ai.intro": "Provider-neutral AI SPI 把不可信的协议资料与样例载荷转换成结构化接入草案；本地 Schema 校验和人工审核始终保留最终决定权。",
  "ai.context": "接入输入",
  "ai.cooling": "电表 · 厂商样例",
  "ai.online": "不可信输入",
  "ai.identity": "厂商",
  "ai.state": "协议",
  "ai.signal": "样例载荷",
  "ai.alarm": "目标",
  "ai.boundary": "仅审核 AI 边界",
  "ai.question": "“这台设备应该如何接入 Octopus？”",
  "ai.answer": "建议接入草案",
  "ai.guard1": "JSON Schema",
  "ai.guard2": "已安装插件",
  "ai.guard3": "人工审核",
  "ai.preview": "预览接入草案",
  "ai.note": "Provider-neutral 草案：不会创建设备、修改模型或下发命令。",
  "ai.previewed": "草案已验证：仍需人工确认后，才能进入正常授权 API。",
  "ai.cleanTitle": "不可信输入",
  "ai.cleanBody": "协议文档和设备载荷始终作为数据处理，不能覆盖系统指令。",
  "ai.guardTitle": "结构化草案",
  "ai.guardBody": "Provider、模型、相关 ID 和 JSON 输出都在本地再次校验。",
  "ai.tenantTitle": "人工批准",
  "ai.tenantBody": "AI 不绕过租户授权，也不能直接写入设备或下发命令。",
  "quick.tag": "本地优先",
  "quick.title": "克隆真实系统。<br><em>看清每个信号去了哪里。</em>",
  "quick.intro": "不是封闭的演示云。Java 服务、本地基础设施、Kubernetes 清单、设计文档和测试都放在同一个 Apache-2.0 仓库中。",
  "quick.java": "运行时",
  "quick.path": "幂等接入",
  "quick.license": "许可证",
  "quick.copy": "复制",
  "quick.copied": "已复制",
  "quick.step1": "克隆平台",
  "quick.step2": "启动本地基础设施",
  "quick.step3": "验证所有边界",
  "quick.read": "打开完整 README ↗",
  "cap.tag": "v1.0 已经具备的基础能力",
  "cap.identity": "设备身份",
  "cap.telemetry": "遥测处理",
  "cap.shadow": "设备状态",
  "cap.commands": "远程命令",
  "cap.alarms": "告警事件",
  "cap.tenancy": "多租户",
  "closing.tag": "构建下一个连接器",
  "closing.title": "增加协议。<br><em>保留平台。</em>",
  "closing.github": "在 GitHub 探索 ↗",
  "closing.release": "查看 v1.0 Release",
  "footer.statement": "一个让多种设备语言汇入同一条数据链路的开源 AIoT 基础平台。",
  "footer.changelog": "变更记录"
};

const protocols = {
  en: {
    mqtt: {
      name: "MQTT 3.1.1 / JSON",
      status: "BUILT IN",
      accent: "blue",
      live: true,
      snippet: "transport: mqtt\ncodec: json\ndelivery: qos1\nstate: ready",
      result: "Connected — normalized telemetry is flowing."
    },
    aws: {
      name: "AWS IoT / X.509",
      status: "PROVIDER ADAPTER",
      accent: "orange",
      live: true,
      snippet: "provider: aws-iot\nidentity: x509\nroute: device-command\nstate: adapter-ready",
      result: "Adapter route ready — command lifecycle is traceable."
    },
    http: {
      name: "HTTP / CoAP",
      status: "PLUGIN CONTRACT",
      accent: "mint",
      live: false,
      snippet: "transport: plugin\ncodec: json | protobuf\ncontract: DeviceTransportPlugin\nstate: implement-connector",
      result: "Contract ready — implement the connector to go live."
    },
    fieldbus: {
      name: "Modbus / OPC UA / BACnet",
      status: "EDGE PLUGIN PATTERN",
      accent: "yellow",
      live: false,
      snippet: "run_at: edge\ndrivers: modbus | opc-ua | bacnet\nbridge: normalized-envelope\nstate: extension-pattern",
      result: "Edge pattern selected — add a driver and normalization map."
    }
  },
  "zh-CN": {
    mqtt: {
      name: "MQTT 3.1.1 / JSON",
      status: "已内置",
      accent: "blue",
      live: true,
      snippet: "传输: mqtt\n编解码: json\n交付: qos1\n状态: 可用",
      result: "连接成功——归一化遥测正在持续进入平台。"
    },
    aws: {
      name: "AWS IoT / X.509",
      status: "云厂商适配器",
      accent: "orange",
      live: true,
      snippet: "服务商: aws-iot\n身份: x509\n路由: device-command\n状态: 适配器就绪",
      result: "适配器路由就绪——命令生命周期可以完整追踪。"
    },
    http: {
      name: "HTTP / CoAP",
      status: "插件契约",
      accent: "mint",
      live: false,
      snippet: "传输: plugin\n编解码: json | protobuf\n契约: DeviceTransportPlugin\n状态: 待实现连接器",
      result: "插件契约已就绪——实现连接器后即可上线。"
    },
    fieldbus: {
      name: "Modbus / OPC UA / BACnet",
      status: "边缘插件模式",
      accent: "yellow",
      live: false,
      snippet: "运行位置: edge\n驱动: modbus | opc-ua | bacnet\n桥接: normalized-envelope\n状态: 扩展模式",
      result: "边缘模式已选定——下一步增加驱动与数据归一化映射。"
    }
  }
};

const dynamicEn = {
  "lab.copied": "Copied",
  "lab.running": "Connecting…",
  "quick.copied": "Copied"
};

const sourceText = Object.fromEntries(
  [...document.querySelectorAll("[data-i18n]")].map((element) => [element.dataset.i18n, element.textContent])
);
const sourceHtml = Object.fromEntries(
  [...document.querySelectorAll("[data-i18n-html]")].map((element) => [element.dataset.i18nHtml, element.innerHTML])
);
const sourceContent = Object.fromEntries(
  [...document.querySelectorAll("[data-i18n-content]")].map((element) => [element.dataset.i18nContent, element.content])
);
const sourceAria = Object.fromEntries(
  [...document.querySelectorAll("[data-i18n-aria-label]")].map((element) => [element.dataset.i18nAriaLabel, element.getAttribute("aria-label")])
);

const englishTitle = document.title;
let currentLocale = readPreferredLocale();
let activeProtocol = "mqtt";
let animationTimers = [];

function normalizeLocale(value) {
  return String(value || "").toLowerCase().startsWith("zh") ? "zh-CN" : "en";
}

function readPreferredLocale() {
  try {
    return normalizeLocale(localStorage.getItem("octopus.locale") || navigator.language);
  } catch {
    return normalizeLocale(navigator.language);
  }
}

function textFor(key) {
  return currentLocale === "zh-CN" && zh[key] ? zh[key] : (sourceText[key] || dynamicEn[key] || key);
}

function clearAnimationTimers() {
  animationTimers.forEach(window.clearTimeout);
  animationTimers = [];
}

function resetConnectionSteps() {
  document.querySelectorAll(".connection-steps .step").forEach((step, index) => {
    step.classList.toggle("is-ready", index === 0);
    step.classList.remove("is-planned");
  });
  document.querySelector("#patch-board").classList.remove("is-connecting", "is-connected");
}

function renderProtocol(key) {
  const item = protocols[currentLocale][key] || protocols.en[key];
  if (!item) return;
  activeProtocol = key;

  document.querySelectorAll("[data-protocol]").forEach((button) => {
    const selected = button.dataset.protocol === key;
    button.classList.toggle("is-active", selected);
    button.setAttribute("aria-selected", String(selected));
  });

  document.querySelector("#protocol-name").textContent = item.name;
  document.querySelector("#protocol-status").textContent = item.status;
  document.querySelector("#protocol-snippet").textContent = item.snippet;
  document.querySelector("#patch-board").dataset.accent = item.accent;
  document.querySelector("#demo-status").textContent = textFor("lab.note");
  document.querySelector("#connect-demo").disabled = false;
  document.querySelector("#connect-demo span:last-child").textContent = textFor("lab.run");
  clearAnimationTimers();
  resetConnectionSteps();
}

function applyLocale(locale, { persist = true } = {}) {
  currentLocale = normalizeLocale(locale);
  const isChinese = currentLocale === "zh-CN";

  document.documentElement.lang = currentLocale;
  document.documentElement.dataset.locale = currentLocale;
  document.title = isChinese ? zh["document.title"] : englishTitle;

  document.querySelectorAll("[data-i18n]").forEach((element) => {
    const key = element.dataset.i18n;
    element.textContent = isChinese && zh[key] ? zh[key] : sourceText[key];
  });
  document.querySelectorAll("[data-i18n-html]").forEach((element) => {
    const key = element.dataset.i18nHtml;
    element.innerHTML = isChinese && zh[key] ? zh[key] : sourceHtml[key];
  });
  document.querySelectorAll("[data-i18n-content]").forEach((element) => {
    const key = element.dataset.i18nContent;
    element.content = isChinese && zh[key] ? zh[key] : sourceContent[key];
  });
  document.querySelectorAll("[data-i18n-aria-label]").forEach((element) => {
    const key = element.dataset.i18nAriaLabel;
    element.setAttribute("aria-label", isChinese && zh[key] ? zh[key] : sourceAria[key]);
  });
  document.querySelectorAll("[data-locale]").forEach((button) => {
    const selected = button.dataset.locale === currentLocale;
    button.classList.toggle("is-active", selected);
    button.setAttribute("aria-pressed", String(selected));
  });

  renderProtocol(activeProtocol);

  if (persist) {
    try {
      localStorage.setItem("octopus.locale", currentLocale);
    } catch {
      // Switching languages still works when storage is unavailable.
    }
  }
}

async function copyText(value, button, copiedKey) {
  const original = button.textContent;
  try {
    await navigator.clipboard.writeText(value);
    button.textContent = textFor(copiedKey);
    window.setTimeout(() => { button.textContent = original; }, 1500);
  } catch {
    button.textContent = value;
    window.setTimeout(() => { button.textContent = original; }, 2200);
  }
}

document.querySelectorAll("[data-locale]").forEach((button) => {
  button.addEventListener("click", () => applyLocale(button.dataset.locale));
});

document.querySelectorAll("[data-protocol]").forEach((button) => {
  button.addEventListener("click", () => renderProtocol(button.dataset.protocol));
});

document.querySelector("#connect-demo")?.addEventListener("click", (event) => {
  const item = protocols[currentLocale][activeProtocol];
  const board = document.querySelector("#patch-board");
  const steps = [...document.querySelectorAll(".connection-steps .step")];
  const button = event.currentTarget;

  clearAnimationTimers();
  resetConnectionSteps();
  button.disabled = true;
  button.querySelector("span:last-child").textContent = textFor("lab.running");
  document.querySelector("#demo-status").textContent = textFor("lab.running");
  void board.offsetWidth;
  board.classList.add("is-connecting");

  animationTimers.push(window.setTimeout(() => steps[1].classList.add(item.live ? "is-ready" : "is-planned"), 520));
  animationTimers.push(window.setTimeout(() => {
    if (item.live) {
      steps[2].classList.add("is-ready");
      board.classList.add("is-connected");
    } else {
      steps[2].classList.add("is-planned");
    }
    document.querySelector("#demo-status").textContent = item.result;
    button.disabled = false;
    button.querySelector("span:last-child").textContent = textFor("lab.run");
  }, 1250));
});

document.querySelector("#copy-protocol")?.addEventListener("click", (event) => {
  copyText(protocols[currentLocale][activeProtocol].snippet, event.currentTarget, "lab.copied");
});

document.querySelector("#copy-quickstart")?.addEventListener("click", (event) => {
  const commands = "git clone https://github.com/fishered/octopus.git\ncd octopus\ndocker compose up -d\n./mvnw verify";
  copyText(commands, event.currentTarget, "quick.copied");
});

document.querySelector("#agent-action")?.addEventListener("click", () => {
  document.querySelector("#agent-note").textContent = currentLocale === "zh-CN"
    ? zh["ai.previewed"]
    : "Draft validated — human approval is still required before any authorized API can run.";
});

applyLocale(currentLocale, { persist: false });

window.addEventListener("pagehide", clearAnimationTimers);
