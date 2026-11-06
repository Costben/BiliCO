# BiliCO

哔哩哔哩第三方客户端。用 Jetpack Compose 与 COUI 组件库从零写起，目标是轻、快、流畅。

包名 `erl.bilico`，最低支持 Android 8.0（API 26）。

## 功能

- **首页**：推荐、排行榜、番剧三个列表；底栏切换动态、直播与个人页
- **播放**：手势调亮度与音量、横向滑动调进度、长按倍速；分 P 与选集自动连播；从上次进度续播；CC 字幕；后台播放与画中画
- **动态**：关注动态流，支持视频、图文、转发、专栏卡片
- **阅读**：专栏与图文动态阅读页，带评论
- **评论**：表情显示为图片，支持发表、回复与点赞
- **收藏与历史**：收藏夹、稍后再看、观看历史（含续播进度）
- **关注**：关注列表与 UP 主空间
- **消息**：回复我的、@ 我的、收到的赞、系统通知
- **追番**：番剧列表与播放
- **直播**：直播流播放与实时弹幕
- **登录**：扫码登录

## 构建

需要 JDK 21 与 Android SDK，然后：

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21   # macOS Homebrew 的 JDK 21
./gradlew assembleRelease
```

产物在 `app/build/outputs/apk/release/app-release.apk`。

界面组件来自 COUI，以本地 Maven 快照方式解析：优先 `../coui-local-repo`，其次 `../coui/build/repository/local`。两个目录都不存在时构建仍可进行，但会缺少 COUI 依赖，需要先构建 COUI 的本地构件仓。

## 签名

release 使用自己的密钥，材料放在仓库根目录的 `keystore.properties` 与 `*.jks`，两者都在 `.gitignore` 内，不入库：

```properties
storeFile=bilico-release.jks
storePassword=********
keyAlias=bilico
keyPassword=********
```

`keystore.properties` 缺失时自动退回 debug 签名，方便没有密钥的机器构建。密钥一旦丢失，已发布的版本无法覆盖升级，务必单独备份。

## 版本号规则

- `versionName` 用 `MAJOR.MINOR.PATCH`。MAJOR 给不兼容的大改版，MINOR 给成批新功能，PATCH 给修 bug。
- `versionCode` 由 `versionName` 推出：`MAJOR * 10000 + MINOR * 100 + PATCH`，保证单调递增且与名称对得上。

当前的 `0.1.0` 对应 `versionCode 100`。

## 目录

```
app/    界面、导航、播放器与各功能页面
data/   接口、数据模型、仓库与登录态
```

## 声明

非官方客户端，与哔哩哔哩官方无关，仅供学习交流。所有内容版权归原作者与原平台所有。
