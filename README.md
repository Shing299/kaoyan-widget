# 考研英语单词小组件 (kaoyan-widget)

一个运行在 Android 上的 **考研英语单词定时推送** 桌面小组件 + App。
每天按计划推送一组单词（桌面小组件 + 通知），并根据距离初试的天数自动倒推每日新学词量。

> ⚠️ **本项目由 AI 生成**
> 本仓库的源码、构建脚本与文档均由 **AI（大语言模型）** 生成，经人工测试与调试后开源。
> 仅供学习交流使用，请自行评估（尤其是定时/通知/权限相关行为）后再安装使用。

---

## 📸 截图

| 权限引导（首次启动） | 主界面 · 今日单词 | 菜单 |
| :---: | :---: | :---: |
| ![权限引导](docs/screenshot-permission.png) | ![主界面](docs/screenshot-main.png) | ![菜单](docs/screenshot-menu.png) |

> 截图为实机运行效果（OnePlus / ColorOS）。首次启动会申请通知权限，并弹窗引导开启自启动、关闭电池优化。

## ✨ 功能特性

- **首次启动设置向导**：第一次打开时引导设置初试日期，未设置前不会推送到通知。
- **初试日期可配置**：默认 `2027-12-20`，可在 App 内随时修改。
- **按剩余天数自动倒推每日词量**：
  - `dailyNew = ceil((词库总数 - 已学)/剩余天数)`，并夹在 `1 ~ 300` 之间；
  - 复习上限 `reviewCap = max(40, dailyNew)`。
- **词库可替换**：内置考研词表（5398 词），支持导入自定义 `txt`（`#` 注释、多分隔符），也支持一键恢复内置词库。
- **定时推送**：通过 `AlarmManager` 定时触发，桌面小组件刷新 + 通知栏推送。
- **开机自恢复**：`BootReceiver` 在开机后重新注册闹钟。
- **随剩余天数动态调整**：每天根据已学进度重新推算当天计划。

## 🧱 项目结构

```
kaoyan-widget/
├─ AndroidManifest.xml
├─ build.sh                     # 构建脚本（密码/密钥由环境变量传入，仓库不含密钥）
├─ assets/words.txt             # 内置考研词表（5398 词）
├─ res/                         # 资源（布局 / 图标 / 字符串 / 小组件信息）
└─ src/com/kaoyan/widget/
   ├─ MainActivity.java         # App 主界面入口
   ├─ HtmlView.java             # 基于 WebView 的界面与设置向导
   ├─ Bridge.java               # JS 与原生交互桥（定时任务用命名静态内部类）
   ├─ Engine.java               # 学习计划推算核心（天数/每日词量/复习上限）
   ├─ Words.java                # 词库加载（内置 / 自定义）
   ├─ Store.java                # 本地状态存储（state.json）
   ├─ Scheduler.java            # 定时调度（AlarmManager）
   ├─ Notifier.java             # 通知
   ├─ WidgetProvider.java       # 桌面小组件
   ├─ BatchReceiver.java        # 定时批量推送接收器
   ├─ BootReceiver.java         # 开机自恢复
   └─ BotService.java           # 前台服务
```

## 🔧 构建

环境要求：Termux（或 Linux）+ 可用的 `aapt2 / javac / d8 / zipalign / apksigner`，
以及 Android SDK 的 `android.jar`；Python3 用于把 `classes.dex` 注入 APK。

> 注意：需使用本机 OS 的 **shell 内置 `>>`** 读取 heredoc；`d8` 建议使用不针对匿名内部类报错的版本（本项目源码已避免使用匿名内部类）。

```bash
# 1) 指定 android.jar 与你的签名密钥（密钥请自行生成，勿提交到仓库）
export ANDROID_JAR=$HOME/sdk/ex/android-34/android.jar
export KS=/path/to/your.jks
export KS_PASS=你的密钥库口令
export KEY_PASS=你的密钥口令

# 2) 构建
bash build.sh
# 产物：out/app.apk
```

安装（示意）：

```bash
adb install -r -d out/app.apk
# 或手机本地：
pm install -r -d out/app.apk
```

## 🔒 隐私说明

本仓库**不包含**任何签名密钥、口令、Token 或用户数据：

- 签名密钥（`*.jks` / `*.keystore` 等）与口令一律 **不提交**（已列入 `.gitignore`）；
- 运行数据（`state.json`、`history.jsonl`、自定义词库等）**不提交**；
- 仓库中不含任何账号、Token、API Key、个人身份信息。

## 📄 许可证

MIT License，详见 [LICENSE](LICENSE)。

## 🤖 声明

本项目（含代码与文档）由 **AI 生成**。