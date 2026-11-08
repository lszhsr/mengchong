#!/bin/sh
# 萌宠伙伴 —— 零依赖构建脚本（aapt2 + javac + d8/dx + zipalign + apksigner）
#
# 用法：
#   ./build.sh /path/to/android-sdk
#   ANDROID_HOME=/path/to/android-sdk ./build.sh
#
# 手动指定工具（Termux 等没有完整 SDK 的场景）：
#   JAR=/path/android.jar AAPT2=aapt2 D8=dx APKSIGNER=apksigner ZIPALIGN=zipalign ./build.sh
set -e

SDK="${1:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}}"

BT=""
PLAT=""
if [ -n "$SDK" ] && [ -d "$SDK" ]; then
    BT=$(ls -d "$SDK/build-tools/"*/ 2>/dev/null | sort -V | tail -1)
    BT=${BT%/}
    PLAT=$(ls -d "$SDK/platforms/android-"*/ 2>/dev/null | sort -V | tail -1)
    PLAT=${PLAT%/}
fi

# 允许逐个覆盖
JAR="${JAR:-}"
AAPT2="${AAPT2:-}"
D8="${D8:-}"
ZIPALIGN="${ZIPALIGN:-}"
APKSIGNER="${APKSIGNER:-}"

if [ -z "$JAR" ] && [ -n "$PLAT" ]; then JAR="$PLAT/android.jar"; fi
if [ -z "$AAPT2" ] && [ -n "$BT" ]; then AAPT2="$BT/aapt2"; fi
if [ -z "$D8" ] && [ -n "$BT" ]; then
    if [ -x "$BT/d8" ]; then D8="$BT/d8"; elif [ -x "$BT/dx" ]; then D8="$BT/dx"; fi
fi
if [ -z "$ZIPALIGN" ] && [ -n "$BT" ]; then ZIPALIGN="$BT/zipalign"; fi
if [ -z "$APKSIGNER" ] && [ -n "$BT" ]; then APKSIGNER="$BT/apksigner"; fi

missing() {
    echo "缺少: $1" >&2
    echo "请装 Android SDK 后用参数指定: ./build.sh /path/to/android-sdk" >&2
    echo "或单独指定: JAR=... AAPT2=... D8=... ZIPALIGN=... APKSIGNER=..." >&2
    exit 1
}
[ -n "$JAR" ] && [ -f "$JAR" ] || missing "android.jar (platforms/android-XX/android.jar)"
[ -n "$AAPT2" ] && (command -v "$AAPT2" >/dev/null 2>&1) || missing "aapt2"
command -v javac >/dev/null 2>&1 || missing "javac (JDK 17+)"
command -v keytool >/dev/null 2>&1 || missing "keytool (JDK)"
[ -n "$D8" ] && (command -v "$D8" >/dev/null 2>&1) || missing "d8 / dx"

OUT=build
rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "[1/6] 编译资源"
"$AAPT2" compile --dir app/src/main/res -o "$OUT/res.zip"

echo "[2/6] 链接清单与资源"
"$AAPT2" link \
    -o "$OUT/app-unsigned.apk" \
    -I "$JAR" \
    --manifest app/src/main/AndroidManifest.xml \
    -R "$OUT/res.zip" \
    --java "$OUT/gen" \
    --min-sdk-version 24 \
    --target-sdk-version 33 \
    --version-code 1 \
    --version-name 1.0

echo "[3/6] 编译 Java"
find app/src/main/java "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -encoding UTF-8 -source 1.8 -target 1.8 -nowarn \
    -bootclasspath "$JAR" -d "$OUT/classes" @"$OUT/sources.txt"

echo "[4/6] 转换 dex"
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
case "$D8" in
    *dx) "$D8" --dex --min-sdk-version=24 --output="$OUT/dex/classes.dex" @"$OUT/classes.txt" ;;
    *)   "$D8" --lib "$JAR" --min-api 24 --output "$OUT/dex" @"$OUT/classes.txt" ;;
esac

pack_dex() {
    if command -v zip >/dev/null 2>&1; then
        (cd "$OUT/dex" && zip -q ../app-unsigned.apk classes.dex)
    else
        # 没有 zip 时：用 jar 工具（JDK 自带）
        (cd "$OUT/dex" && jar uf ../app-unsigned.apk classes.dex)
    fi
}
pack_dex

echo "[5/6] 对齐"
if [ -n "$ZIPALIGN" ] && command -v "$ZIPALIGN" >/dev/null 2>&1; then
    "$ZIPALIGN" -f 4 "$OUT/app-unsigned.apk" "$OUT/app-aligned.apk"
else
    echo "  (没有 zipalign，跳过对齐，一般也能装)"
    cp "$OUT/app-unsigned.apk" "$OUT/app-aligned.apk"
fi

echo "[6/6] 签名"
if [ ! -f debug.keystore ]; then
    keytool -genkeypair -keystore debug.keystore -storepass android -keypass android \
        -alias androiddebugkey -dname "CN=Android Debug,O=Android,C=US" \
        -keyalg RSA -keysize 2048 -validity 10000
fi
if [ -n "$APKSIGNER" ] && command -v "$APKSIGNER" >/dev/null 2>&1; then
    "$APKSIGNER" sign --ks debug.keystore --ks-pass pass:android \
        --key-pass pass:android --ks-key-alias androiddebugkey \
        --out "$OUT/app-release.apk" "$OUT/app-aligned.apk"
else
    echo "  没找到 apksigner，改用 jarsigner（v1 签名，Android 11+ 可能装不上）"
    cp "$OUT/app-aligned.apk" "$OUT/app-release.apk"
    jarsigner -keystore debug.keystore -storepass android -keypass android \
        "$OUT/app-release.apk" androiddebugkey
fi

echo
echo "完成: $(pwd)/$OUT/app-release.apk"
