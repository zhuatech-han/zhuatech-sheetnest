<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted } from "vue";
import {
  Layers,
  BarChart3,
  Users,
  ShieldCheck,
  Settings,
  LogOut,
  Plus,
  Search,
  ArrowRight,
  ChevronLeft,
  ChevronRight,
  X,
  Download,
  RefreshCw,
  Clock3,
  ExternalLink,
} from "@lucide/vue";
import { api, resetCsrf } from "./api.js";
import {
  actions,
  states,
  actionNames,
  payload,
  millimeters,
  squareMeters,
} from "./domain.js";
import { fields } from "./forms.js";
const lang = ref(localStorage.getItem("sheetnest-language") || "zh"),
  me = ref(null),
  view = ref("jobs"),
  busy = ref(false),
  error = ref(""),
  notice = ref(""),
  loginForm = ref({ username: "", password: "" }),
  options = ref({}),
  directories = ref({}),
  rows = ref([]),
  total = ref(0),
  page = ref(0),
  search = ref(""),
  filter = ref(""),
  sort = ref("newest"),
  detail = ref(null),
  stats = ref({}),
  modal = ref(null),
  form = ref({}),
  contact = ref(false),
  tab = ref("layout"),
  boardIndex = ref(0),
  oldRevision = ref(null);
const t = (zh, en) => (lang.value === "zh" ? zh : en);
const can = (p) => me.value?.permissions?.includes(p);
const adminTypes = [
  "users",
  "roles",
  "departments",
  "menus",
  "permissions",
  "dictionaries",
  "settings",
];
const labels = {
  jobs: ["排样方案", "Nesting jobs"],
  dashboard: ["排样统计", "Statistics"],
  audit: ["操作审计", "Audit"],
  users: ["账号管理", "Accounts"],
  roles: ["角色与权限", "Roles"],
  departments: ["部门管理", "Departments"],
  menus: ["导航管理", "Navigation"],
  permissions: ["权限目录", "Permissions"],
  dictionaries: ["材料类型", "Material types"],
  settings: ["系统参数", "Settings"],
};
const icons = {
  jobs: Layers,
  dashboard: BarChart3,
  audit: Clock3,
  users: Users,
  roles: ShieldCheck,
};
const title = computed(() =>
  t(...(labels[view.value] || ["SheetNest", "SheetNest"])),
);
const state = (s) => t(...(states[s] || [s || "—", s || "—"]));
const actionName = (a) => t(...actionNames[a]);
const editable = computed(
  () =>
    detail.value?.canWrite &&
    ["DRAFT", "CALCULATED"].includes(detail.value.status),
);
const currentActions = computed(() => actions(detail.value));
const layout = computed(
  () => oldRevision.value?.layout || detail.value?.layout,
);
const board = computed(() => layout.value?.boards?.[boardIndex.value]);
const failures = {
  UNAUTHENTICATED: ["登录已失效，请重新登录", "Session expired"],
  FORBIDDEN: ["当前岗位无此权限", "Permission required"],
  OUT_OF_SCOPE: ["超出账号授权范围", "Outside your scope"],
  FROZEN: ["方案或结果已经冻结", "Record frozen"],
  STALE_VERSION: ["记录已变化，请刷新后重试", "Record changed; refresh first"],
  INDEPENDENT_REVIEW_REQUIRED: [
    "复核人和登记人须与所有方案编辑人相互独立",
    "Reviewer and operator must be independent of every editor",
  ],
  ASSIGNED_OPERATOR_ONLY: [
    "须由指定实物登记人操作",
    "Assigned operator required",
  ],
  ASSIGNED_ACCOUNT_UNAVAILABLE: [
    "指定账号已停用、部门不符或权限不足",
    "Assigned account unavailable",
  ],
  INVALID_DIMENSION: [
    "尺寸最多一位小数，请检查范围",
    "Use dimensions within bounds with at most one decimal",
  ],
  INVALID_DESIGN: [
    "检查板材尺寸、留边、切缝、板数及零件需求",
    "Check sheet, margin, kerf, sheet count and parts",
  ],
  INVALID_COUNTS: [
    "实际数量须为非负整数，合计不得超过需求",
    "Use nonnegative counts within demand",
  ],
  INCOMPLETE_RESULT: [
    "逐项补齐合格、报废和未切数量，合计须等于需求",
    "Account for every part as good, scrap or not cut",
  ],
  ACKNOWLEDGEMENT_REQUIRED: [
    "先确认收悉方案，当前阶段才可登记实物",
    "Acknowledge the plan during result recording",
  ],
  UNPLACED_PARTS: [
    "仍有零件未排入，请修订方案后重新计算",
    "Unplaced parts remain; revise and recalculate",
  ],
  CALCULATION_REQUIRED: ["请先计算排样", "Calculate a layout first"],
  SNAPSHOT_CHANGED: [
    "冻结快照不一致，操作被阻止",
    "Snapshot changed; action blocked",
  ],
  INVALID_STATE: ["当前状态不允许操作", "Action unavailable in this state"],
  INVALID_INPUT: ["检查必填字段和输入范围", "Check required fields and bounds"],
  INVALID_OUTCOME: ["请选择实际申报为完成或停止", "Choose finished or stopped"],
  IDENTITY_IMMUTABLE: [
    "方案编号和负责部门不可修改",
    "Reference and department are immutable",
  ],
  CONFLICT: ["编号重复或记录被引用", "Duplicate or referenced record"],
  LOGIN_FAILED: ["账号或密码不正确", "Incorrect credentials"],
  LOGIN_THROTTLED: ["登录尝试过多，稍后重试", "Too many attempts"],
  LAST_ADMIN: [
    "至少保留一个启用的全范围管理员",
    "Keep an enabled full administrator",
  ],
  WEAK_PASSWORD: [
    "密码至少12位，包含大小写字母和数字",
    "Use 12+ characters, upper/lower case and digits",
  ],
  REQUEST_KEY_REUSED: [
    "请求键已用于其他内容，请重新打开表单",
    "Request key already used",
  ],
  PART_LIMIT: [
    "每方案最多30种、合计300件零件",
    "Maximum 30 part types and 300 pieces",
  ],
  REVISION_LIMIT: [
    "每方案最多保留50次计算，请新建后续方案",
    "Maximum 50 calculations per job",
  ],
  RECORD_LIMIT: ["已达方案数量上限", "Job limit reached"],
  COMMAND_LIMIT: ["已达操作记录上限", "Command limit reached"],
};
function language() {
  lang.value = lang.value === "zh" ? "en" : "zh";
  localStorage.setItem("sheetnest-language", lang.value);
  document.documentElement.lang = lang.value === "zh" ? "zh-CN" : "en";
}
function clearSession() {
  me.value = null;
  detail.value = null;
  rows.value = [];
  options.value = {};
  directories.value = {};
  modal.value = null;
  oldRevision.value = null;
  resetCsrf();
}
async function run(fn) {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    return await fn();
  } catch (e) {
    error.value = failures[e.message]
      ? t(...failures[e.message])
      : t("操作失败：", "Action failed: ") + e.message;
    if (e.message === "UNAUTHENTICATED") clearSession();
    if (["FORBIDDEN", "OUT_OF_SCOPE"].includes(e.message)) {
      detail.value = null;
      oldRevision.value = null;
      rows.value = [];
    }
  } finally {
    busy.value = false;
  }
}
async function loadOptions() {
  if (can("job.read")) options.value = await api("/options");
  if (can("admin") && me.value.scope === "ALL")
    for (const key of ["roles", "permissions", "departments"])
      directories.value[key] = await api("/admin/" + key);
}
async function load() {
  detail.value = null;
  oldRevision.value = null;
  if (view.value === "dashboard") {
    stats.value = await api("/dashboard");
    return;
  }
  if (view.value === "jobs") {
    const r = await api(
      "/jobs?" +
        new URLSearchParams({
          search: search.value,
          status: filter.value,
          page: String(page.value),
          size: "12",
          sort: sort.value,
        }),
    );
    rows.value = r.rows;
    total.value = r.total;
  } else {
    const all = (
      await api(view.value === "audit" ? "/audit" : "/admin/" + view.value)
    ).filter((r) =>
      Object.values(r).some(
        (v) =>
          typeof v === "string" &&
          v.toLowerCase().includes(search.value.toLowerCase()),
      ),
    );
    all.sort((a, b) => (sort.value === "oldest" ? a.id - b.id : b.id - a.id));
    total.value = all.length;
    rows.value = all.slice(page.value * 12, page.value * 12 + 12);
  }
}
async function navigate(code) {
  if (busy.value) return;
  rows.value = [];
  total.value = 0;
  view.value = code;
  page.value = 0;
  search.value = "";
  filter.value = "";
  await run(load);
  window.scrollTo(0, 0);
}
async function signIn() {
  await run(async () => {
    resetCsrf();
    me.value = await api("/auth/login", "POST", loginForm.value);
    loginForm.value.password = "";
    await loadOptions();
    view.value = me.value.menus[0]?.code || "jobs";
    await load();
  });
}
async function signOut() {
  await run(async () => {
    await api("/auth/logout", "POST", {});
    clearSession();
  });
}
async function open(row) {
  await run(async () => {
    detail.value = await api("/jobs/" + row.id);
    oldRevision.value = null;
    tab.value = "layout";
    boardIndex.value = 0;
    window.scrollTo(0, 0);
  });
}
async function refresh() {
  await run(async () => {
    me.value = await api("/auth/me");
    await loadOptions();
    oldRevision.value = null;
    if (detail.value) detail.value = await api("/jobs/" + detail.value.id);
    else await load();
  });
}
function edit(type, row = null) {
  error.value = "";
  modal.value = { kind: "save", type, row };
  form.value = {
    departmentId: me.value.departmentId,
    category: "WOOD",
    enabled: true,
    type: "category",
    scope: "DEPARTMENT",
    rotation: false,
    permissions: [],
    ...row,
    requestKey: crypto.randomUUID(),
    version: detail.value?.version,
  };
  if (type === "users") form.value.password = "";
  if (type === "roles") form.value.permissions = [...(row?.permissions || [])];
}
function command(action) {
  error.value = "";
  modal.value = { kind: "command", type: "command", action };
  form.value = {
    requestKey: crypto.randomUUID(),
    version: detail.value.version,
    note: "",
    outcome: "FINISHED",
  };
}
function actual(part) {
  error.value = "";
  const row = detail.value.results.find((r) => r.partId === part.id);
  modal.value = { kind: "actual", type: "results", part };
  form.value = {
    good: "",
    scrap: "",
    notCut: "",
    note: "",
    ...row,
    requestKey: crypto.randomUUID(),
    version: detail.value.version,
  };
}
function remove(type, row) {
  error.value = "";
  modal.value = {
    kind: adminTypes.includes(type) ? "adminDelete" : "delete",
    type: "command",
    target: type,
    row,
  };
  form.value = {
    requestKey: crypto.randomUUID(),
    version: detail.value?.version,
    note: "",
  };
}
const modalFields = computed(() =>
  modal.value?.kind === "password"
    ? [
        ["oldPassword", "原密码", "Current password", "password"],
        ["newPassword", "新密码", "New password", "password"],
      ]
    : fields[modal.value?.type] || [],
);
function choices(key) {
  if (key === "scope")
    return ["ALL", "DEPARTMENT", "SELF"].map((code) => ({
      code,
      name: t(
        ...{
          ALL: ["全部", "All"],
          DEPARTMENT: ["本部门", "Department"],
          SELF: ["本人相关", "Own records"],
        }[code],
      ),
    }));
  if (["reviewers", "operators"].includes(key))
    return (options.value.accounts || []).filter(
      (a) =>
        a.departmentId === Number(form.value.departmentId) &&
        a.id !== me.value.id &&
        a.permissions.includes(
          key === "reviewers" ? "job.review" : "cut.write",
        ),
    );
  return directories.value[key] || options.value[key] || [];
}
async function save() {
  await run(async () => {
    const m = modal.value;
    let result;
    if (m.kind === "password") {
      await api(
        "/auth/password",
        "POST",
        payload(form.value, modalFields.value),
      );
      clearSession();
      notice.value = t(
        "密码已更改，请重新登录",
        "Password changed; sign in again",
      );
      return;
    }
    if (m.kind === "adminDelete")
      await api("/admin/" + m.target + "/" + m.row.id, "DELETE");
    else if (m.kind === "command") {
      const body = {
        requestKey: form.value.requestKey,
        version: form.value.version,
        note: form.value.note,
      };
      if (m.action === "submit-report") body.outcome = form.value.outcome;
      result = await api(
        "/jobs/" + detail.value.id + "/commands/" + m.action,
        "POST",
        body,
      );
    } else if (m.kind === "delete") {
      result = await api("/parts/" + m.row.id + "/delete", "POST", {
        requestKey: form.value.requestKey,
        version: form.value.version,
        jobId: detail.value.id,
        note: form.value.note,
      });
    } else {
      const body = payload(form.value, modalFields.value),
        isAdmin = adminTypes.includes(m.type);
      if (!isAdmin) {
        body.requestKey = form.value.requestKey;
        body.version = form.value.version;
      }
      if (m.kind === "actual") {
        body.jobId = detail.value.id;
        body.partId = m.part.id;
        result = await api("/results", "POST", body);
      } else {
        if (m.type === "parts") body.jobId = detail.value.id;
        result = await api(
          (isAdmin ? "/admin" : "") +
            "/" +
            m.type +
            (m.row ? "/" + m.row.id : ""),
          m.row ? "PUT" : "POST",
          body,
        );
      }
    }
    modal.value = null;
    oldRevision.value = null;
    notice.value = t("已保存", "Saved");
    await loadOptions();
    if (detail.value) detail.value = await api("/jobs/" + detail.value.id);
    else if (m.type === "jobs" && result) {
      detail.value = await api("/jobs/" + result.id);
      tab.value = "layout";
      boardIndex.value = 0;
    } else await load();
  });
}
async function revision(row) {
  await run(async () => {
    oldRevision.value = await api("/revisions/" + row.id);
    tab.value = "layout";
    boardIndex.value = 0;
  });
}
async function download(kind) {
  await run(async () => {
    const path =
      "/jobs/" +
      detail.value.id +
      "/" +
      (kind === "json" ? "report.json" : "parts.csv");
    let blob;
    if (kind === "json")
      blob = new Blob([JSON.stringify(await api(path), null, 2)], {
        type: "application/json",
      });
    else {
      const response = await fetch("/api" + path);
      if (!response.ok) throw new Error((await response.json()).code);
      blob = await response.blob();
    }
    const url = URL.createObjectURL(blob),
      a = document.createElement("a");
    a.href = url;
    a.download = "sheetnest-" + detail.value.id + "." + kind;
    a.click();
    URL.revokeObjectURL(url);
  });
}
function time(value) {
  return value
    ? new Intl.DateTimeFormat(lang.value === "zh" ? "zh-CN" : "en-GB", {
        timeZone: "Asia/Shanghai",
        dateStyle: "short",
        timeStyle: "short",
      }).format(new Date(value))
    : "—";
}
function person(id) {
  return (
    options.value.accounts?.find((a) => a.id === id)?.displayName || String(id)
  );
}
function partName(id) {
  return detail.value?.parts.find((p) => p.id === id)?.code || "#" + id;
}
function partResult(id) {
  return detail.value?.results.find((p) => p.partId === id);
}
function color(id) {
  return ["#c9ddb4", "#eccfa5", "#b9d7d9", "#d0c4dd", "#c9d5eb", "#e3bbbb"][
    Number(id) % 6
  ];
}
function area(value) {
  return squareMeters(value).toFixed(4);
}
function value(row, key) {
  if (key.endsWith("At")) return time(row[key]);
  if (key === "status") return state(row[key]);
  if (key === "enabled") return row[key] ? t("是", "Yes") : t("否", "No");
  if (key === "permissions")
    return (row.permissions?.length || 0) + t(" 项权限", " permissions");
  const lookup =
    key === "roleId"
      ? directories.value.roles
      : key === "departmentId"
        ? directories.value.departments || options.value.departments
        : null;
  return lookup?.find((v) => v.id === row[key])?.name ?? row[key] ?? "—";
}
const columns = computed(
  () =>
    ({
      jobs: [
        ["reference", "方案编号", "Reference"],
        ["name", "方案名称", "Name"],
        ["material", "材料规格", "Material"],
        ["width", "宽 mm", "Width mm"],
        ["height", "高 mm", "Height mm"],
        ["status", "状态", "Status"],
      ],
      settings: [
        ["code", "参数", "Setting"],
        ["value", "参数值", "Value"],
      ],
      audit: [
        ["createdAt", "时间", "Time"],
        ["actor", "账号", "Actor"],
        ["action", "动作", "Action"],
        ["objectId", "记录", "Record"],
      ],
    })[view.value] ||
    (fields[view.value] || []).filter((f) => f[0] !== "password").slice(0, 5),
);
const canCreate = computed(() =>
  view.value === "jobs"
    ? can("job.write")
    : can("admin") &&
      ["users", "roles", "departments", "dictionaries"].includes(view.value),
);
onMounted(async () => {
  document.documentElement.lang = lang.value === "zh" ? "zh-CN" : "en";
  try {
    me.value = await api("/auth/me");
    await loadOptions();
    view.value = me.value.menus[0]?.code || "jobs";
    await load();
  } catch (e) {
    if (e.message !== "UNAUTHENTICATED")
      error.value = t("连接失败，请刷新页面", "Connection failed; refresh");
  }
});
</script>
<template>
  <div v-if="!me" class="login-layout">
    <section class="login-art" aria-hidden="true">
      <div class="art-title">
        SheetNest<span>01 / RECTANGULAR NESTING</span>
      </div>
      <div class="art-sheet">
        <i class="shape one"></i><i class="shape two"></i
        ><i class="shape three"></i><i class="shape four"></i
        ><i class="shape five"></i><span class="measure">W × H</span>
      </div>
      <div class="art-note">
        {{
          t(
            "板材排样 · 方案复核 · 实物登记",
            "Sheet layout · Independent review · Actual results",
          )
        }}
      </div>
    </section>
    <section class="login-panel">
      <div class="brand">
        <img src="/brand/logo.jpg" alt="知华科技" /><button
          class="plain"
          @click="language"
        >
          {{ lang === "zh" ? "EN" : "中文" }}
        </button>
      </div>
      <form class="login-card" @submit.prevent="signIn">
        <div class="eyebrow">SHEETNEST / {{ t("工作空间", "WORKSPACE") }}</div>
        <h1>{{ t("登录工作空间", "Sign in to your workspace") }}</h1>
        <p>
          {{
            t("板材排样与切割结果复核", "Sheet nesting & cutting result review")
          }}
        </p>
        <label
          >{{ t("账号", "Username")
          }}<input
            v-model="loginForm.username"
            autocomplete="username"
            required
            maxlength="60"
        /></label>
        <label
          >{{ t("密码", "Password")
          }}<input
            v-model="loginForm.password"
            type="password"
            autocomplete="current-password"
            required
            maxlength="72"
        /></label>
        <p v-if="error" role="alert" class="error">{{ error }}</p>
        <p v-if="notice" role="status">{{ notice }}</p>
        <button class="primary login-button" :disabled="busy">
          {{ busy ? t("正在登录…", "Signing in…") : t("登录", "Sign in")
          }}<ArrowRight :size="17" />
        </button>
        <small>{{
          t(
            "公开源码学习版 · 非商业使用",
            "Public source learning edition · Noncommercial use",
          )
        }}</small>
      </form>
      <footer>
        {{
          t(
            "知华科技（上海如静知华信息科技有限公司）",
            "ZhuaTech · Shanghai Rujing Zhihua Information Technology Co., Ltd.",
          )
        }}<button class="plain" @click="contact = true">
          {{ t("联系知华科技", "Contact ZhuaTech") }}
        </button>
      </footer>
    </section>
  </div>
  <div v-else class="workspace">
    <aside class="sidebar">
      <div class="brand"><img src="/brand/logo.jpg" alt="知华科技" /></div>
      <div class="product-name">
        <Layers :size="22" /><span
          >SheetNest<small>{{ t("板材排样", "Sheet nesting") }}</small></span
        >
      </div>
      <nav :aria-label="t('主导航', 'Main navigation')">
        <button
          v-for="m in me.menus"
          :key="m.id"
          :class="{ active: view === m.code }"
          :disabled="busy"
          @click="navigate(m.code)"
        >
          <component :is="icons[m.code] || Settings" :size="18" /><span>{{
            lang === "zh" ? m.name : m.nameEn
          }}</span
          ><span v-if="view === m.code" class="nav-dot"></span>
        </button>
      </nav>
      <div class="sidebar-end">
        <span>{{ t("公开源码学习版", "Source learning edition") }}</span
        ><button class="plain" @click="contact = true">
          {{ t("商业授权／定制咨询", "Licensing / custom development")
          }}<ExternalLink :size="12" />
        </button>
      </div>
    </aside>
    <div class="main-area">
      <header class="topbar">
        <span>{{ options.companyName || "SheetNest" }}</span>
        <div>
          <button class="plain" @click="language">
            {{ lang === "zh" ? "EN" : "中文" }}</button
          ><span class="avatar">{{ me.displayName.slice(0, 1) }}</span
          ><span class="user-name"
            >{{ me.displayName }}<small>{{ me.role }}</small></span
          ><button
            class="plain"
            @click="
              modal = { kind: 'password', type: 'password' };
              form = { oldPassword: '', newPassword: '' };
            "
          >
            {{ t("改密", "Password") }}</button
          ><button
            class="icon-button"
            :aria-label="t('退出登录', 'Sign out')"
            :disabled="busy"
            @click="signOut"
          >
            <LogOut :size="17" />
          </button>
        </div>
      </header>
      <main>
        <div class="page-heading">
          <div>
            <div class="eyebrow">
              SHEETNEST / {{ t("工作空间", "WORKSPACE") }}
            </div>
            <h1>{{ detail ? detail.name : title }}</h1>
            <p v-if="detail">
              {{ detail.reference }} <span class="separator">/</span>
              <span class="status" :data-status="detail.status">{{
                state(detail.status)
              }}</span>
              <span class="version">v{{ detail.version }}</span>
            </p>
            <p v-else>
              {{
                t(
                  "当前授权范围内的记录",
                  "Records within your authorized scope",
                )
              }}
            </p>
          </div>
          <div class="heading-actions">
            <button
              v-if="detail"
              class="secondary"
              :disabled="busy"
              @click="run(load)"
            >
              <ChevronLeft :size="16" />{{ t("返回列表", "Back") }}</button
            ><button
              class="icon-button"
              :disabled="busy"
              :aria-label="t('刷新', 'Refresh')"
              @click="refresh"
            >
              <RefreshCw :size="18" /></button
            ><button
              v-if="!detail && canCreate"
              class="primary"
              :disabled="busy"
              @click="edit(view)"
            >
              <Plus :size="17" />{{ t("新建", "New") }}
            </button>
          </div>
        </div>
        <p v-if="error" role="alert" class="error banner">{{ error }}</p>
        <p v-if="notice && !modal" role="status" class="notice banner">
          {{ notice }}
        </p>
        <template v-if="detail">
          <section class="job-summary">
            <div>
              <small>{{ t("材料与板材", "Material & sheet") }}</small
              ><strong>{{ detail.material }}</strong
              ><span
                >{{ detail.width }} × {{ detail.height }} mm ·
                {{ t("最多", "Max") }} {{ detail.maxSheets }}
                {{ t("张", "sheets") }}</span
              >
            </div>
            <div>
              <small>{{ t("切割参数", "Cut parameters") }}</small
              ><strong>{{ t("切缝", "Kerf") }} {{ detail.kerf }} mm</strong
              ><span
                >{{ t("四边留边", "Edge margin") }} {{ detail.margin }} mm</span
              >
            </div>
            <div>
              <small>{{ t("独立复核／实物登记", "Reviewer / operator") }}</small
              ><strong>{{ person(detail.reviewerId) }}</strong
              ><span>{{ person(detail.operatorId) }}</span>
            </div>
            <div>
              <small>{{ t("当前结果", "Outcome") }}</small
              ><strong>{{ state(detail.outcome) }}</strong
              ><span>{{ time(detail.closedAt) }}</span>
            </div>
          </section>
          <div class="action-bar">
            <div class="tabs">
              <button
                v-for="item in [
                  ['layout', '排样图', 'Layout'],
                  ['parts', '零件需求', 'Parts'],
                  ['results', '实物结果', 'Actual results'],
                  ['history', '版本与记录', 'History'],
                ]"
                :key="item[0]"
                :class="{ selected: tab === item[0] }"
                @click="tab = item[0]"
              >
                {{ t(item[1], item[2]) }}
              </button>
            </div>
            <div>
              <button
                v-for="a in currentActions"
                :key="a"
                class="secondary small"
                :disabled="busy"
                @click="command(a)"
              >
                {{ actionName(a) }}
              </button>
            </div>
          </div>
          <section v-if="tab === 'layout'" class="layout-section">
            <div v-if="oldRevision" class="history-alert">
              {{ t("正在查看历史计算", "Viewing historical calculation") }} #{{
                oldRevision.revision.id
              }}<button
                class="plain"
                @click="
                  oldRevision = null;
                  boardIndex = 0;
                "
              >
                {{ t("返回当前方案", "Back to current plan") }}
              </button>
            </div>
            <template v-if="layout"
              ><div class="layout-metrics">
                <div>
                  <span>{{ t("使用板数", "Used sheets") }}</span
                  ><strong>{{ layout.usedSheets }}</strong>
                </div>
                <div>
                  <span>{{ t("排入数量", "Placed pieces") }}</span
                  ><strong
                    >{{ layout.placed
                    }}<small> / {{ layout.requested }}</small></strong
                  >
                </div>
                <div>
                  <span>{{
                    t("零件面积／使用整板面积", "Part area / used sheet area")
                  }}</span
                  ><strong
                    >{{ Number(layout.utilization).toFixed(2)
                    }}<small>%</small></strong
                  >
                </div>
                <div>
                  <span>{{ t("排样状态", "Layout status") }}</span
                  ><strong class="metric-status">{{
                    state(layout.status)
                  }}</strong>
                </div>
              </div>
              <div class="layout-grid">
                <section class="drawing-card">
                  <header>
                    <h2>{{ t("板材布局", "Sheet layout") }}</h2>
                    <select
                      v-model="boardIndex"
                      :aria-label="t('选择板材', 'Select sheet')"
                    >
                      <option
                        v-for="(b, i) in layout.boards"
                        :key="b.number"
                        :value="i"
                      >
                        {{ t("第", "Sheet") }} {{ b.number }} {{ t("张", " ") }}
                      </option>
                    </select>
                  </header>
                  <div v-if="board" class="sheet-canvas">
                    <svg
                      :viewBox="`0 0 ${layout.width} ${layout.height}`"
                      role="img"
                      :aria-label="
                        t('实际计算的板材排样图', 'Calculated sheet layout')
                      "
                    >
                      <rect
                        x="0"
                        y="0"
                        :width="layout.width"
                        :height="layout.height"
                        fill="#ebe7dd"
                      />
                      <rect
                        :x="layout.margin"
                        :y="layout.margin"
                        :width="layout.width - 2 * layout.margin"
                        :height="layout.height - 2 * layout.margin"
                        fill="#faf9f5"
                      />
                      <rect
                        v-for="(o, i) in board.offcuts"
                        :key="'o' + i"
                        :x="o.x"
                        :y="o.y"
                        :width="o.width"
                        :height="o.height"
                        fill="#efeee9"
                        stroke="#ddd9d0"
                        :stroke-width="Math.max(layout.width / 1000, 1)"
                      />
                      <g
                        v-for="p in board.placements"
                        :key="p.partId + '-' + p.piece"
                      >
                        <rect
                          :x="p.x"
                          :y="p.y"
                          :width="p.width"
                          :height="p.height"
                          :fill="color(p.partId)"
                          stroke="#ffffff"
                          :stroke-width="Math.max(layout.width / 1100, 1)"
                        />
                        <text
                          :x="p.x + p.width / 2"
                          :y="p.y + p.height / 2"
                          text-anchor="middle"
                          dominant-baseline="middle"
                          :font-size="
                            Math.min(
                              layout.width / 45,
                              p.width / 8,
                              p.height / 4,
                            )
                          "
                          fill="#27372b"
                        >
                          {{ partName(p.partId) }}·{{ p.piece
                          }}{{ p.rotated ? "↻" : "" }}
                        </text>
                        <title>
                          {{ partName(p.partId) }} #{{ p.piece }} ·
                          {{ millimeters(p.width) }} ×
                          {{ millimeters(p.height) }} mm
                        </title>
                      </g>
                      <rect
                        v-for="(c, i) in board.cuts"
                        :key="'c' + i"
                        :x="c.axis === 'V' ? c.at : c.x"
                        :y="c.axis === 'V' ? c.y : c.at"
                        :width="c.axis === 'V' ? c.kerf : c.width"
                        :height="c.axis === 'V' ? c.height : c.kerf"
                        fill="#a98156"
                      /></svg
                    ><span
                      >{{ millimeters(layout.width) }} ×
                      {{ millimeters(layout.height) }} mm</span
                    >
                  </div>
                  <div v-else class="empty">
                    {{ t("没有可排入的零件", "No parts fit") }}
                  </div>
                  <footer class="drawing-legend">
                    <span
                      ><i class="legend-part"></i>{{ t("零件", "Parts") }}</span
                    ><span
                      ><i class="legend-kerf"></i>{{ t("切缝", "Kerf") }}</span
                    ><span
                      ><i class="legend-offcut"></i
                      >{{ t("余料", "Offcuts") }}</span
                    ><span>↻ {{ t("旋转90°", "Rotated 90°") }}</span>
                  </footer>
                </section>
                <section class="area-card">
                  <div class="eyebrow">
                    {{ t("面积账", "AREA ACCOUNTING") }}
                  </div>
                  <h2>{{ t("可逐项复核", "Accounted for") }}</h2>
                  <dl>
                    <div
                      v-for="item in [
                        ['partArea', '零件', 'Parts'],
                        ['kerfArea', '切缝', 'Kerf'],
                        ['leftoverArea', '余料', 'Offcuts'],
                        ['trimArea', '留边', 'Edge margins'],
                      ]"
                      :key="item[0]"
                    >
                      <dt>{{ t(item[1], item[2]) }}</dt>
                      <dd>{{ area(layout[item[0]]) }} m²</dd>
                    </div>
                    <div class="area-total">
                      <dt>{{ t("使用整板", "Used full sheets") }}</dt>
                      <dd>{{ area(layout.sheetArea) }} m²</dd>
                    </div>
                  </dl>
                  <p>
                    {{
                      t(
                        "启发式排样估算 · 需按实际设备和材料复核",
                        "Heuristic estimate · Review against actual equipment and material",
                      )
                    }}
                  </p>
                  <small
                    >{{ layout.algorithm }}<br />{{ layout.strategy }}</small
                  >
                </section>
              </div>
              <section v-if="layout.unplaced.length" class="unplaced card">
                <h2>{{ t("未排入零件", "Unplaced parts") }}</h2>
                <p v-for="p in layout.unplaced" :key="p.partId">
                  {{ partName(p.partId) }} · {{ p.quantity }}
                  {{ t("件", "pieces") }}
                </p>
              </section>
              <section v-if="board" class="card">
                <h2>
                  {{
                    t("当前板材分割顺序", "Current sheet partition sequence")
                  }}
                </h2>
                <div class="table-scroll">
                  <table>
                    <thead>
                      <tr>
                        <th>#</th>
                        <th>{{ t("子板路径", "Plate path") }}</th>
                        <th>{{ t("方向", "Axis") }}</th>
                        <th>
                          {{ t("绝对坐标 mm", "Absolute coordinate mm") }}
                        </th>
                        <th>
                          {{
                            t("当前子板宽 × 高 mm", "Current plate W × H mm")
                          }}
                        </th>
                        <th>{{ t("切缝 mm", "Kerf mm") }}</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="(c, i) in board.cuts" :key="i">
                        <td>{{ i + 1 }}</td>
                        <td>{{ c.path }}</td>
                        <td>
                          {{
                            c.axis === "V"
                              ? t("纵向", "Vertical")
                              : t("横向", "Horizontal")
                          }}
                        </td>
                        <td>{{ millimeters(c.at) }}</td>
                        <td>
                          {{ millimeters(c.width) }} ×
                          {{ millimeters(c.height) }}
                        </td>
                        <td>{{ millimeters(c.kerf) }}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
                <small>{{
                  t(
                    "顺序为逻辑分割树的先序展开，不包含机床执行参数。",
                    "Sequence follows the logical partition tree; no machine execution parameters.",
                  )
                }}</small>
              </section>
            </template>
            <section v-else class="empty card">
              <Layers :size="36" />
              <h2>{{ t("尚无当前排样结果", "No current layout") }}</h2>
              <p>
                {{
                  t(
                    "完善零件需求后计算排样；修改输入后需重新计算。",
                    "Calculate after defining parts; recalculate when inputs change.",
                  )
                }}
              </p>
            </section>
          </section>
          <section v-else-if="tab === 'parts'" class="card">
            <header class="section-heading">
              <h2>
                {{ t("零件需求", "Part requirements")
                }}<small
                  >{{ detail.parts.reduce((n, p) => n + p.quantity, 0) }} /
                  300</small
                >
              </h2>
              <div>
                <button
                  v-if="editable"
                  class="secondary"
                  @click="edit('jobs', detail)"
                >
                  {{ t("编辑方案", "Edit job") }}</button
                ><button v-if="editable" class="primary" @click="edit('parts')">
                  <Plus :size="16" />{{ t("添加零件", "Add part") }}
                </button>
              </div>
            </header>
            <p class="instructions">{{ detail.instructions }}</p>
            <div class="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>{{ t("编号", "Code") }}</th>
                    <th>{{ t("名称", "Name") }}</th>
                    <th>{{ t("宽 × 高 mm", "W × H mm") }}</th>
                    <th>{{ t("数量", "Quantity") }}</th>
                    <th>{{ t("旋转", "Rotation") }}</th>
                    <th v-if="editable">{{ t("操作", "Actions") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="p in detail.parts" :key="p.id">
                    <td>{{ p.code }}</td>
                    <td>{{ p.name }}</td>
                    <td>{{ p.width }} × {{ p.height }}</td>
                    <td>{{ p.quantity }}</td>
                    <td>
                      {{
                        p.rotation
                          ? t("允许", "Allowed")
                          : t("固定方向", "Fixed orientation")
                      }}
                    </td>
                    <td v-if="editable">
                      <button class="plain" @click="edit('parts', p)">
                        {{ t("编辑", "Edit") }}</button
                      ><button class="plain danger" @click="remove('parts', p)">
                        {{ t("删除", "Delete") }}
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div v-if="!detail.parts.length" class="empty">
              {{ t("尚未添加零件需求", "No part requirements") }}
            </div>
          </section>
          <section v-else-if="tab === 'results'" class="card">
            <header class="section-heading">
              <h2>
                {{ t("人工实际结果", "Manually recorded actual results") }}
              </h2>
              <span>{{
                detail.acknowledgedAt
                  ? t("已收悉", "Acknowledged")
                  : t("尚未收悉", "Not acknowledged")
              }}</span>
            </header>
            <div class="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>{{ t("零件", "Part") }}</th>
                    <th>{{ t("需求", "Demand") }}</th>
                    <th>{{ t("合格", "Good") }}</th>
                    <th>{{ t("报废", "Scrap") }}</th>
                    <th>{{ t("未切", "Not cut") }}</th>
                    <th>{{ t("说明", "Note") }}</th>
                    <th>{{ t("操作", "Actions") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="p in detail.parts" :key="p.id">
                    <td>{{ p.code }}</td>
                    <td>{{ p.quantity }}</td>
                    <td>{{ partResult(p.id)?.good ?? "—" }}</td>
                    <td>{{ partResult(p.id)?.scrap ?? "—" }}</td>
                    <td>{{ partResult(p.id)?.notCut ?? "—" }}</td>
                    <td>{{ partResult(p.id)?.note || "—" }}</td>
                    <td>
                      <button
                        v-if="
                          detail.canOperate &&
                          detail.status === 'RUNNING' &&
                          detail.acknowledgedAt
                        "
                        class="plain"
                        @click="actual(p)"
                      >
                        {{ t("登记结果", "Record results") }}</button
                      ><span v-else>—</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <small>{{
              t(
                "— 表示尚未登记。合格、报废、未切的合计须等于需求后才能提交。",
                "— means not recorded. Good, scrap and not cut must total the demand before submission.",
              )
            }}</small>
            <dl v-if="detail.closedHash" class="seal">
              <dt>{{ t("封存校验值", "Closed snapshot hash") }}</dt>
              <dd>{{ detail.closedHash }}</dd>
            </dl>
          </section>
          <section v-else class="history-grid">
            <section class="card">
              <h2>{{ t("计算版本", "Calculation versions") }}</h2>
              <button
                v-for="r in detail.revisions"
                :key="r.id"
                class="revision-row"
                @click="revision(r)"
              >
                <span
                  ><strong>#{{ r.id }} · {{ r.algorithm }}</strong
                  ><small>{{ time(r.createdAt) }}</small></span
                ><ArrowRight :size="16" />
              </button>
              <p v-if="!detail.revisions.length">
                {{ t("尚无计算版本", "No calculation versions") }}
              </p>
            </section>
            <section class="card">
              <h2>{{ t("操作记录", "Operation history") }}</h2>
              <article
                v-for="e in detail.history"
                :key="e.id"
                class="event-row"
              >
                <span class="event-dot"></span>
                <div>
                  <strong>{{ e.action }} · {{ person(e.actorId) }}</strong>
                  <p>{{ e.note }}</p>
                  <small>{{ time(e.createdAt) }}</small>
                </div>
              </article>
            </section>
          </section>
          <footer class="detail-footer">
            <span
              >{{ t("创建于", "Created") }} {{ time(detail.createdAt) }}</span
            >
            <div v-if="can('export')">
              <button class="plain" :disabled="busy" @click="download('json')">
                <Download :size="15" />JSON</button
              ><button class="plain" :disabled="busy" @click="download('csv')">
                <Download :size="15" />CSV
              </button>
            </div>
          </footer>
        </template>
        <template v-else-if="view === 'dashboard'"
          ><section class="dashboard-grid">
            <div
              v-for="item in [
                ['jobs', '方案数量', 'Jobs'],
                ['usedSheets', '当前排样使用板数', 'Sheets in current layouts'],
                ['placed', '当前排入件数', 'Currently placed pieces'],
                ['good', '已登记合格件数', 'Recorded good pieces'],
              ]"
              :key="item[0]"
              class="stat-card"
            >
              <span>{{ t(item[1], item[2]) }}</span
              ><strong>{{ stats[item[0]] ?? 0 }}</strong>
            </div>
          </section>
          <div class="dashboard-panels">
            <section class="card">
              <h2>{{ t("方案状态", "Job status") }}</h2>
              <div v-for="(n, s) in stats.statuses" :key="s" class="bar-row">
                <span>{{ state(s) }}</span>
                <div>
                  <i
                    :style="{
                      width: (n / Math.max(stats.jobs, 1)) * 100 + '%',
                    }"
                  ></i>
                </div>
                <strong>{{ n }}</strong>
              </div>
              <p v-if="!stats.jobs">
                {{ t("暂无授权范围内的方案", "No jobs within scope") }}
              </p>
            </section>
            <section class="card">
              <h2>{{ t("已登记实物", "Recorded actual pieces") }}</h2>
              <dl>
                <div
                  v-for="item in [
                    ['good', '合格', 'Good'],
                    ['scrap', '报废', 'Scrap'],
                    ['notCut', '未切', 'Not cut'],
                  ]"
                  :key="item[0]"
                >
                  <dt>{{ t(item[1], item[2]) }}</dt>
                  <dd>{{ stats[item[0]] ?? 0 }}</dd>
                </div>
              </dl>
              <h3>{{ t("封存结果", "Closed outcomes") }}</h3>
              <p v-for="(n, s) in stats.outcomes" :key="s">
                {{ state(s) }} · {{ n }}
              </p>
            </section>
          </div></template
        >
        <section v-else class="card list-card">
          <form
            class="filter-bar"
            @submit.prevent="
              page = 0;
              run(load);
            "
          >
            <label class="search"
              ><Search :size="17" /><input
                v-model="search"
                :aria-label="t('搜索记录', 'Search records')"
                :placeholder="t('搜索编号、名称或材料', 'Search records')"
                maxlength="160" /></label
            ><select
              v-if="view === 'jobs'"
              v-model="filter"
              :aria-label="t('状态筛选', 'Filter status')"
            >
              <option value="">{{ t("全部状态", "All statuses") }}</option>
              <option
                v-for="s in [
                  'DRAFT',
                  'CALCULATED',
                  'SUBMITTED',
                  'APPROVED',
                  'RUNNING',
                  'REVIEW',
                  'CLOSED',
                  'CANCELLED',
                ]"
                :key="s"
                :value="s"
              >
                {{ state(s) }}
              </option></select
            ><select v-model="sort" :aria-label="t('排序', 'Sort')">
              <option value="newest">
                {{ t("最新优先", "Newest first") }}
              </option>
              <option value="oldest">
                {{ t("最早优先", "Oldest first") }}
              </option>
              <option v-if="view === 'jobs'" value="reference">
                {{ t("编号排序", "By reference") }}
              </option></select
            ><button class="secondary" :disabled="busy">
              {{ t("查询", "Search") }}
            </button>
          </form>
          <div class="table-scroll">
            <table>
              <thead>
                <tr>
                  <th v-for="c in columns" :key="c[0]">{{ t(c[1], c[2]) }}</th>
                  <th v-if="view !== 'audit'">{{ t("操作", "Actions") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in rows" :key="r.id">
                  <td v-for="c in columns" :key="c[0]">
                    <span
                      v-if="c[0] === 'status'"
                      class="status"
                      :data-status="r.status"
                      >{{ state(r.status) }}</span
                    ><template v-else>{{ value(r, c[0]) }}</template>
                  </td>
                  <td v-if="view !== 'audit'">
                    <button
                      v-if="view === 'jobs'"
                      class="plain"
                      @click="open(r)"
                    >
                      {{ t("打开方案", "Open job")
                      }}<ArrowRight :size="14" /></button
                    ><template v-else
                      ><button class="plain" @click="edit(view, r)">
                        {{ t("编辑", "Edit") }}</button
                      ><button
                        v-if="
                          !['menus', 'permissions', 'settings'].includes(view)
                        "
                        class="plain danger"
                        @click="remove(view, r)"
                      >
                        {{ t("删除", "Delete") }}
                      </button></template
                    >
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-if="!rows.length" class="empty">
            <Layers :size="32" />
            <h3>{{ t("暂无记录", "No records") }}</h3>
            <p>
              {{
                t(
                  "当前筛选和授权范围内没有记录",
                  "No records within this filter and scope",
                )
              }}
            </p>
          </div>
          <footer class="pagination">
            <span
              >{{ t("共", "Total") }} {{ total }}
              {{ t("条记录", "records") }}</span
            >
            <div>
              <button
                class="icon-button"
                :disabled="page === 0 || busy"
                :aria-label="t('上一页', 'Previous page')"
                @click="
                  page--;
                  run(load);
                "
              >
                <ChevronLeft :size="16" /></button
              ><span>{{ page + 1 }}</span
              ><button
                class="icon-button"
                :disabled="(page + 1) * 12 >= total || busy"
                :aria-label="t('下一页', 'Next page')"
                @click="
                  page++;
                  run(load);
                "
              >
                <ChevronRight :size="16" />
              </button>
            </div>
          </footer>
        </section>
        <footer class="workspace-footer">
          <span
            >SheetNest ·
            {{
              t(
                "公开源码学习版／非商业源码版",
                "Public source learning / Noncommercial edition",
              )
            }}</span
          ><a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
            >{{ t("知华科技官网", "ZhuaTech website")
            }}<ExternalLink :size="12"
          /></a>
        </footer>
      </main>
    </div>
  </div>
  <div v-if="modal" class="modal-backdrop" @click.self="modal = null">
    <section
      class="modal"
      role="dialog"
      aria-modal="true"
      aria-labelledby="modal-title"
    >
      <header>
        <h2 id="modal-title">
          {{
            modal.kind === "command"
              ? actionName(modal.action)
              : modal.kind === "password"
                ? t("修改密码", "Change password")
                : ["delete", "adminDelete"].includes(modal.kind)
                  ? t("确认删除", "Confirm deletion")
                  : modal.kind === "actual"
                    ? t("登记结果", "Record results") + " · " + modal.part.code
                    : t("编辑", "Edit") +
                      " · " +
                      t(
                        ...(labels[modal.type] || [
                          modal.type === "parts" ? "零件" : "实物结果",
                          modal.type === "parts" ? "Part" : "Results",
                        ]),
                      )
          }}
        </h2>
        <button
          class="icon-button"
          :aria-label="t('关闭', 'Close')"
          @click="modal = null"
        >
          <X :size="20" />
        </button>
      </header>
      <form @submit.prevent="save">
        <div v-if="modal.kind !== 'adminDelete'" class="form-grid">
          <label
            v-for="f in modalFields"
            :key="f[0]"
            :class="{
              wide: f[3] === 'textarea' || f[3] === 'permissions',
              checkbox: f[3] === 'boolean',
            }"
            ><template v-if="f[3] === 'boolean'"
              ><input v-model="form[f[0]]" type="checkbox" />{{
                t(f[1], f[2])
              }}</template
            ><template v-else
              >{{ t(f[1], f[2]) }}
              <div v-if="f[3] === 'permissions'" class="permission-grid">
                <label v-for="p in directories.permissions || []" :key="p.id"
                  ><input
                    v-model="form.permissions"
                    type="checkbox"
                    :value="p.code"
                  />{{ p.name }}<small>{{ p.code }}</small></label
                >
              </div>
              <select
                v-else-if="['id', 'select'].includes(f[3])"
                v-model="form[f[0]]"
                required
                :disabled="
                  modal.row && modal.type === 'jobs' && f[0] === 'departmentId'
                "
              >
                <option disabled value="">{{ t("请选择", "Select") }}</option>
                <option
                  v-for="o in choices(f[4])"
                  :key="o.id || o.code"
                  :value="f[3] === 'id' ? o.id : o.code"
                >
                  {{
                    o.displayName ||
                    (lang === "en" && o.nameEn ? o.nameEn : o.name)
                  }}
                </option>
              </select>
              <textarea
                v-else-if="f[3] === 'textarea'"
                v-model="form[f[0]]"
                maxlength="1000"
                :required="modal.type !== 'results'"
              ></textarea>
              <input
                v-else
                v-model="form[f[0]]"
                :type="
                  ['integer', 'decimal'].includes(f[3])
                    ? 'number'
                    : f[3] === 'password'
                      ? 'password'
                      : 'text'
                "
                :step="f[3] === 'decimal' ? '0.1' : '1'"
                :min="['integer', 'decimal'].includes(f[3]) ? 0 : undefined"
                :maxlength="f[3] === 'password' ? 72 : 200"
                :required="
                  !(modal.type === 'users' && modal.row && f[0] === 'password')
                "
                :disabled="
                  modal.row && modal.type === 'jobs' && f[0] === 'reference'
                "
                :autocomplete="
                  f[3] === 'password' ? 'new-password' : 'off'
                " /></template
          ></label>
          <label v-if="modal.action === 'submit-report'" class="wide"
            >{{ t("实际申报", "Actual outcome declaration")
            }}<select v-model="form.outcome" required>
              <option value="FINISHED">
                {{
                  t(
                    "加工结束，按实物结果申报",
                    "Finished; report actual results",
                  )
                }}
              </option>
              <option value="STOPPED">
                {{
                  t(
                    "停止加工，逐项注明未切数量",
                    "Stopped; account for uncut pieces",
                  )
                }}
              </option>
            </select></label
          >
        </div>
        <p v-else>
          {{
            t(
              "删除未被引用的记录；已被业务引用的记录会保留。",
              "Delete an unreferenced record; business references prevent deletion.",
            )
          }}
        </p>
        <p v-if="modal.kind === 'actual'" class="form-note">
          {{ t("需求数量", "Demand") }} {{ modal.part.quantity }} ·
          {{
            t(
              "填写实际合格、报废和未切数量，报废或未切须说明原因。",
              "Enter actual counts; explain scrap or uncut pieces.",
            )
          }}
        </p>
        <p v-if="error" role="alert" class="error">{{ error }}</p>
        <footer>
          <button type="button" class="secondary" @click="modal = null">
            {{ t("取消", "Cancel") }}</button
          ><button class="primary" :disabled="busy">
            {{ busy ? t("保存中…", "Saving…") : t("确认保存", "Save") }}
          </button>
        </footer>
      </form>
    </section>
  </div>
  <div v-if="contact" class="modal-backdrop" @click.self="contact = false">
    <section
      class="modal contact-modal"
      role="dialog"
      aria-modal="true"
      aria-labelledby="contact-title"
    >
      <header>
        <h2 id="contact-title">{{ t("联系知华科技", "Contact ZhuaTech") }}</h2>
        <button
          class="icon-button"
          :aria-label="t('关闭', 'Close')"
          @click="contact = false"
        >
          <X :size="20" />
        </button>
      </header>
      <img class="contact-logo" src="/brand/logo.jpg" alt="知华科技" />
      <p>
        {{
          t(
            "上海如静知华信息科技有限公司",
            "Shanghai Rujing Zhihua Information Technology Co., Ltd.",
          )
        }}
      </p>
      <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
        >https://www.zhuatech.cn/</a
      >
      <div class="qr-grid">
        <figure>
          <img src="/brand/wechat-zhuatech.png" alt="知华科技微信 zhuatech" />
          <figcaption>{{ t("咨询微信", "WeChat") }}：zhuatech</figcaption>
        </figure>
        <figure>
          <img src="/brand/wechat-zhuatech2.png" alt="知华科技微信 zhuatech2" />
          <figcaption>{{ t("咨询微信", "WeChat") }}：zhuatech2</figcaption>
        </figure>
      </div>
      <p>
        {{
          t(
            "商业授权 · 定制开发 · 部署 · 系统集成",
            "Commercial licensing · Custom development · Deployment · System integration",
          )
        }}
      </p>
      <small>{{
        t(
          "公开源码学习版／非商业源码版；授权范围以仓库 LICENSE 为准。",
          "Source learning / Noncommercial edition; repository LICENSE defines the terms.",
        )
      }}</small>
    </section>
  </div>
</template>
