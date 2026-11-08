# 萌宠伙伴 (mengchong)

桌面悬浮宠物 App —— 导入一张图片，变成桌面上的可拖动萌宠（可调大小 / 透明度 / 圆角）。

- 包名：`com.dsh.deskpet`
- 纯 Java + 系统控件，零第三方依赖，minSdk 24 / targetSdk 33

## 构建

`build.sh` 只用 aapt2 + javac + d8 + zipalign + apksigner，不需要 Gradle：

```bash
./build.sh /path/to/android-sdk
# 产物 build/app-release.apk
```

## 关于 DeskPetApp.png

它不是图片，而是整个工程的 `tar.gz` 包（手机浏览器的文件选择器只显示图片，所以改了后缀）。
解开：

```bash
tar -xzf DeskPetApp.png      # 得到 DeskPetApp/ 目录
```

`.github/workflows/build.yml` 就是按这个方式解包并自动构建 APK 的。
