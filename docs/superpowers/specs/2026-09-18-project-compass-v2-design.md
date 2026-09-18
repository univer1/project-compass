# 「项目罗盘」v2.0 设计文档：等待截止必填 + 安卓 APP 封装

- 日期：2026-09-18
- 状态：设计已获用户批准，待实施
- 前置：v1.2（68 项功能回归 + 10 项 30 项目承载测试全过）

## 变更记录（本次）

### v2.0（2026-09-18）：移除期望天数 + 原生 APP

两部分改动：应用层简化等待提醒机制；网页封装为独立安卓 APP（含每日早报通知）。

## 0. 用户确认的决策

| 决策点 | 用户选择 |
|---|---|
| 未设截止的等待如何提醒 | **等待必须设截止**（截止是唯一时间轴，必填） |
| 系统级提醒 | **要，每日早报**（每天 8:00 通知栏推送当日摘要） |
| 安装方式 | **可侧载 APK**（手机无管控限制） |
| 现有数据 | **真实数据在用**（迁移流程必须严谨） |
| 封装路线 | **A：手写极简 WebView 壳**（零第三方依赖、无网络权限） |

## 1. 应用层改动（项目罗盘.html）

### 1.1 移除「期望 N 天回复」

**删除项**（涉及约 25 处代码）：

- 数据层：`waitings[].expect` 字段、`settings.waitExpectDays`、`wExpect()`、`wOverdue()` 期望语义
- 录入：编辑器等待行的期望天数输入、快速记等待弹层的期望输入、设置页「默认期望天数」项
- 展示：「超 X 天，该催了」「X 天内正常」等期望相关文案（全景卡片、等待页、今日页、周回顾、转交文本）
- 派生：`pOverdue()` 重定义为「项目存在截止已过的等待」；全景筛选 chip「等待超时」→「等待截止已过」

### 1.2 截止必填规则

- 编辑器保存等待行、快速记等待保存时：`due` 为空 → 阻止保存，红字提示「请设置截止日期」
- 编辑器等待行的截止输入框视觉上标注必填

### 1.3 各视图联动

- 等待页排序：截止已过 > 今天/临近截止（3 天内）> 已等最久
- 今日页催办建议：只由「等待截止已过 / 今天截止」驱动（先于事项逾期，维持 v1.2 次序）
- 周回顾「待催办」段落：按截止驱动；无截止等待不存在（因必填）
- 全景卡片等待行：「等 谁 · 什么 · 已等 N 天 · X 天后截止（或 截止已过 N 天）」

### 1.4 数据迁移（打开应用 / 导入旧备份时）

- 旧等待无 `due` → 自动补 `due = 今天 + 7 天`，统计补设数量，弹一次性提示「N 条旧等待已自动设为 7 天后截止，请到编辑器检查调整」
- `expect` / `waitExpectDays` 字段静默删除
- v1 / v1.1 / v1.2 备份文件导入走同一迁移通道
- 数据版本号升为 v4（`version: 4`）

## 2. Android APP 壳（原生层）

### 2.1 工程结构

```
android/
├── app/
│   ├── src/main/
│   │   ├── java/com/compass/proj/     # MainActivity + Bridge + Alarm + Icons
│   │   ├── assets/index.html          # 应用层单文件（同一份源码）
│   │   └── res/                       # 图标（代码生成的罗盘造型 PNG）
│   └── build.gradle
├── build.gradle / settings.gradle / gradle.properties
└── gradlew
```

- 构建：Gradle 7.6 + AGP 7.4.2（Java 11 兼容）、compileSdk 33、minSdk 24、targetSdk 33
- **零第三方依赖**（不引 AndroidX，纯 android.app 框架 API）
- **Manifest 不申请 INTERNET 权限**：APP 物理断网，涉密最严格形态；WebView `file:///android_asset/` 加载本地资源无需网络权限
- APK 体积预期 1-2MB

### 2.2 MainActivity 与 JSBridge

- WebView：`domStorageEnabled`（localStorage 生效）、返回键 = 关闭弹层/退出、状态栏配色与应用主题一致
- `Android.exportBackup(json)`：ACTION_CREATE_DOCUMENT 系统保存文件器（用户可选 Downloads/U 盘）
- `Android.importBackup()`：ACTION_OPEN_FILE 读 JSON 回传 JS（onActivityResult → evaluateJavascript）
- `Android.saveDailyDigest(json)`：JS 每次数据变化时把当日摘要（计数 + 前 3 条要点）写入 SharedPreferences，供早报使用
- `Android.setAlarm(hour, minute, on)`：设置/取消每日闹钟（早报开关与时间存 SharedPreferences）
- `Android.requestNotif()`：Android 13+ 运行时请求 POST_NOTIFICATIONS

### 2.3 每日早报

- AlarmManager `setInexactRepeating`（无需 SCHEDULE_EXACT_ALARM 权限，±15 分钟系统误差，早报场景可接受）
- BroadcastReceiver 触发 → 读 SharedPreferences 快照 → NotificationManager 发通知
- 通知文案：`项目罗盘：逾期 X 项 · 等待该催 Y 项 · 临近截止 Z 项`（无快照时发通用提示「点击查看今日安排」）
- 点通知 → 打开 APP 落到今日页（Intent extra）
- 应用层设置页新增「每日提醒」区块：开关 + 时间（默认 08:00）

### 2.4 应用层设置页（APP 形态下）

- 网页形态（浏览器打开）：导出/导入继续用现有下载/文件选择方式，早报区块不显示
- APP 形态（检测 `window.Android` 桥存在）：导出/导入走系统文件器，显示「每日提醒」区块
- 同一份 HTML 两种形态自适应，网页版功能完全保留（可继续作为备份入口）

## 3. 数据迁移流程（真实数据，严谨模式）

1. 旧网页版（手机 Chrome）：设置 → 导出备份 → 得到 JSON 文件
2. 传文件到新 APP 可达位置（同手机，文件管理器任意目录）
3. 新 APP：设置 → 导入备份 → 自动迁移（补截止/删期望）→ 显示核对页「项目 N · 事项 N · 等待 N（其中补设截止 N 条）」
4. 用户核对无误 → 生效
5. **旧网页版保留**，用户验证数日后再弃用（不主动引导删除）

## 4. 测试计划

- 应用层（jsdom）：
  - 现有回归更新：期望相关断言全部改写；等待页排序、今日页次序、筛选 chip 重验
  - 新增：截止必填校验（编辑器/快速弹层）、旧数据迁移（补 7 天截止 + 提示）、v1.x 备份导入迁移、APP 桥存在时的设置页分支
- 原生层：
  - 摘要快照解析的 Java 单元测试（JUnit，不依赖设备）
  - 构建产物验证：APK 产出、manifest 权限清单（确认无 INTERNET）
- 手工验证清单（交付时附）：迁移往返、早报触发、通知点击落页、断网全功能

## 5. 风险与备选

| 风险 | 应对 |
|---|---|
| 沙箱走代理下载 Android 构建链（~500MB：Gradle + SDK）失败 | 交付完整工程包 + 一条命令构建说明（用户任意电脑可构建）；先尝试沙箱内构建 |
| Android 13+ 通知权限被拒 | 设置页显示状态引导再次授权 |
| inexact 闹钟被厂商省电策略延迟 | 常见机型可接受；交付说明中列出「电池优化白名单」建议 |
| WebView localStorage 清除应用数据即丢 | 保留 >30 天未导出提醒；APP 私有目录比 Chrome 更难被误清 |

## 6. 交付物

1. `项目罗盘.html` v2.0（应用层，网页形态继续可用）
2. `项目罗盘.apk`（或可构建工程包，视沙箱构建结果）
3. 更新的 `使用说明.md`（迁移流程 + APP 安装说明）
4. 本设计文档
