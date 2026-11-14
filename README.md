<div align="center">

<img src="favicon.png" width="96" height="96" alt="BiliCO" />

# BiliCO

**轻量、流畅的哔哩哔哩第三方 Android 客户端**

Kotlin · Jetpack Compose · COUI（ColorOS 17 设计语言）

[![Release](https://img.shields.io/github/v/release/Costben/BiliCO?include_prereleases&label=Release&color=FB7299&style=flat-square)](https://github.com/Costben/BiliCO/releases)
![Android](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?style=flat-square&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![UI](https://img.shields.io/badge/UI-COUI%2017-111111?style=flat-square)

[功能一览](#-功能一览) · [下载体验](#-下载体验) · [本地构建](#-本地构建) · [致谢](#-致谢)

</div>

---

**BiliCO**（包名 `erl.bilico`）是基于 Jetpack Compose 与 [COUI 17](https://github.com/Costben/coui17) 从零构建的哔哩哔哩第三方客户端，面向 **Android 8.0（API 26）+** 设备，追求轻量、低延迟响应与顺滑自然的原生级动效体验。

## ✨ 功能一览

| 模块 | 主要特性 |
| :--- | :--- |
| **首页与发现** | 底栏包含 **推荐 / 动态 / 我的**；推荐页集成 **推荐流、排行榜、番剧、直播** 四栏分段切换，支持双击底栏回顶刷新 |
| **全屏搜索** | 展开式搜索面板，支持热搜发现、搜索历史、实时联想建议，以及视频 / 用户 / 专栏分类与排序筛选 |
| **视频与直播** | 基于 Media3 ExoPlayer 的 DASH / HLS 播放，支持亮度音量与进度手势、长按倍速、分 P 选集与播放队列、自动连播、续播记忆、CC 字幕、CDN 线路切换、后台播放与画中画；支持直播流与实时弹幕 |
| **弹幕与评论** | 内置弹幕渲染、重复弹幕合并小字标及屏蔽/样式设置；评论区支持图片表情、楼中楼详情、发表回复与点赞 |
| **动态与阅读** | 支持 **全部 / 视频 / 直播** 动态筛选，覆盖视频、图文（含大图查看）、转发与专栏阅读页 |
| **个人与消息** | 支持扫码/网页登录及多账号切换；覆盖历史记录、收藏夹（四宫格封面）、稍后再看、追番、关注、UP 主空间及消息中心（私信、回复、@、赞、系统消息），列表支持多选批量与左滑删除 |
| **界面与更新** | 连续曲率圆角、毛玻璃顶/底栏、卡片展开转场与预测返回手势；内置 GitHub Releases 更新检查与多镜像测速下载 |

## 📦 下载体验

前往 [Releases](https://github.com/Costben/BiliCO/releases) 下载最新 APK 安装即可。应用内「设置 → 检查更新」也支持直接检测新版与预发布版本并完成下载安装。

## 🛠️ 本地构建

### 环境要求

- **JDK 21**
- **Android SDK**（项目配置为 `compileSdk 37` / `targetSdk 37` / `minSdk 26`）
- **COUI 17 本地构件**：界面组件库 `io.github.suqi8.coui.kmp:*-android:1.2.0-SNAPSHOT` 从仓库上级目录读取，按 `../coui-local-repo` → `../coui/build/repository/local` 顺序解析，需先拉取并构建 [Costben/coui17](https://github.com/Costben/coui17)

### 编译 APK

```bash
./gradlew assembleRelease
```

构建产物输出在：

```text
app/build/outputs/apk/release/app-release.apk
```

<details>
<summary>关于 Release 签名</summary>

未在根目录放置 `keystore.properties` 时，Release 构建会自动回退为 debug 签名，克隆源码后可直接打包调试。如需使用正式密钥签名，在根目录提供 `keystore.properties` 与对应 `.jks` 文件即可（均已加入 `.gitignore`）。

</details>

## 📁 项目结构

```text
BiliCO/
├── app/    # 界面与交互（Compose UI、导航转场、播放器、弹幕与各功能页）
└── data/   # 网络与数据层（Bilibili API、Wbi/App 签名、直播弹幕、仓库与会话存储）
```

## 🙏 致谢

- **UI 组件与视觉**：[COUI 17](https://github.com/Costben/coui17) · [COUI Kit](https://github.com/ItosEO/COUI-Kit) · [COUI](https://github.com/suqi8/coui) · [Miuix](https://github.com/compose-miuix-ui/miuix)
- **功能与协议参考**：[PiliPlus](https://github.com/bggRGjQaUbCoE/PiliPlus) · [BiliPai](https://github.com/jay3-yy/BiliPai)
- **核心开源依赖**：[AndroidX Media3](https://github.com/androidx/media) · [Coil 3](https://github.com/coil-kt/coil) · [OkHttp](https://github.com/square/okhttp) · Retrofit · [ZXing](https://github.com/zxing/zxing) · Brotli

## 📄 声明

> 本项目为第三方哔哩哔哩客户端，与哔哩哔哩官方无关，仅供个人学习与研究交流。所有内容、商标与版权归原平台及对应开源项目作者所有。
