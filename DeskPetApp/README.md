# 萌宠伙伴（MengChong）— 复刻版

参考原「桌面萌宠」重新实现的独立 App：导入一张图片 → 变成桌面上的悬浮宠物，
可拖动、可调大小 / 透明度 / 圆角，带图片、功能、对话三个页面。

- 包名：`com.dsh.deskpet`（和原版 `com.example.deskpet` 不同，可同时安装）
- 纯 Java + 系统控件，**零第三方依赖**（不需要 AndroidX / Compose / Gradle 插件）
- minSdk 24，targetSdk 33

## 功能对照

| 页面 | 功能 |
| --- | --- |
| 图片 | 导入图片（系统文件选择器）、缩略图预览、点击看大图、长按删除 |
| 功能 | 悬浮窗权限引导、显示/隐藏桌宠、大小 60–320dp、透明度 20–100%、圆角 0–100% 滑杆、关闭桌宠 |
| 对话 | 本地气泡对话（内置若干回复，纯本地，不联网） |
| 桌宠 | 悬浮在所有应用之上，拖动移动，点击有缩放弹跳，前台服务常驻通知可一键关闭 |

## 目录

```
DeskPetApp/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/dsh/deskpet/
│   │   ├── MainActivity.java   # 三个页面的界面与逻辑
│   │   ├── PetService.java     # 悬浮桌宠前台服务
│   │   └── PetPrefs.java       # 设置项读写
│   └── res/                    # 应用名与图标
├── build.sh                    # 零依赖构建脚本
└── .github/workflows/build-apk.yml   # 云端自动出包
```

## 出包方式

### 方式 A：GitHub Actions（不需要电脑，手机就能完成）

1. 手机浏览器打开 github.com，新建一个仓库（Private 也可以）。
2. 在仓库页点 **Add file → Upload files**，把本文件夹里的
   `app/`、`build.sh`、`.github/` 整个拖进去提交。
   （手机上操作：用支持多选上传的浏览器，或先压缩成 zip 上传后再解压提交）
3. 打开 **Actions** 标签，等 Build APK 跑完（约 1–2 分钟）。
4. 在该次运行页面底部 **Artifacts** 下载 `MengChong-app-release`，
   解压得到 `app-release.apk`，点开安装即可。

### 方式 B：电脑 / Termux 命令行

需要：JDK 17+、Android SDK（`build-tools;34.0.0` + `platforms;android-34`）、`zip`。

```bash
./build.sh /path/to/android-sdk
# 产物：build/app-release.apk
```

Termux 上大致是：

```bash
pkg install openjdk-17 zip aapt aapt2 apksigner
# 还需要一个带 platforms/android-34/android.jar 的 SDK 目录，
# 可从 https://dl.google.com/android/repository/platform-34_r03.zip 解压获得
./build.sh ~/android-sdk
```

### 方式 C：Android Studio

用 Android Studio 新建 Empty 工程（包名 `com.dsh.deskpet`，语言 Java），
把 `app/src/main` 下四个文件按同样路径拷进去，删掉自动生成的 MainActivity，
点 Run / Build APK 即可。

## 使用说明

1. 首次打开：图片页 → 导入图片（选一张抠好背景的 PNG 效果最好）。
2. 功能页 → 开启悬浮窗 → 系统设置里允许「显示在其他应用上层」→ 回到 App 点「显示桌宠」。
3. 桌宠出现后可拖动；点一下会弹跳；停靠可用功能页的滑杆实时调整。
4. 想关掉：通知栏「关闭」，或功能页「关闭桌宠」。

## 说明

- 所有数据只存在本机应用私有目录（图片存为 `files/pet.png`），不联网、无广告。
- 原 APK 的 `res/` 里没有可复用的业务资源，界面与逻辑均为重新实现。
