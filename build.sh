#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# 考研英语单词桌面小组件 —— 构建脚本
#
# 依赖（Termux + android-sdk / aapt2 / d8 / zipalign / apksigner）：
#   - Android SDK 的 android.jar（默认取 $HOME/sdk/ex/android-34/android.jar）
#   - 一个签名用 keystore（.jks），通过下面环境变量传入，仓库中不包含密钥
#
# 用法：
#   ANDROID_JAR=/path/to/android.jar \
#   KS=release.jks KS_PASS=你的密码 KEY_PASS=你的密码 \
#   bash build.sh
#
# 产物：out/app.apk
# ---------------------------------------------------------------------------
set -u

HERE="$(cd "$(dirname "$0")" && pwd)"

ANDROID_JAR="${ANDROID_JAR:-$HOME/sdk/ex/android-34/android.jar}"
KS="${KS:-keystore.jks}"
KS_PASS="${KS_PASS:-changeit}"
KEY_PASS="${KEY_PASS:-$KS_PASS}"

OUT="$HERE/out"

# javac / java 有时不在 PATH 里（例如 Termux 里 $PREFIX/bin/javac 是坏软链），显式找一次 JDK
if ! command -v javac >/dev/null 2>&1; then
  for cand in "${PREFIX:-/data/data/com.termux/files/usr}"/lib/jvm/java-*-openjdk \
              "${JAVA_HOME:-/nonexistent}"; do
    if [ -x "$cand/bin/javac" ]; then
      JAVA_HOME="$cand"
      PATH="$cand/bin:$PATH"
      export JAVA_HOME PATH
      break
    fi
  done
fi

if ! command -v javac >/dev/null 2>&1; then
  echo "找不到 javac：请安装 JDK，或把它的 bin 目录加进 PATH / 设置 JAVA_HOME"
  exit 1
fi

if [ ! -f "$ANDROID_JAR" ]; then
  echo "android.jar 不存在: $ANDROID_JAR"
  echo "请通过环境变量 ANDROID_JAR 指定路径"
  exit 1
fi

# 清理上一次的中间产物（避免脏 dex）
"$HERE/clean.sh" 2>/dev/null || python3 -c "import shutil;shutil.rmtree('$OUT/c',ignore_errors=True);shutil.rmtree('$OUT/dx',ignore_errors=True)"

mkdir -p "$OUT/gen" "$OUT/c" "$OUT/dx"

echo "--- aapt2 compile ---"
aapt2 compile --dir "$HERE/res" -o "$OUT/res.zip" || exit 2

echo "--- aapt2 link ---"
aapt2 link -A "$HERE/assets" -o "$OUT/base.apk" -I "$ANDROID_JAR" \
  --manifest "$HERE/AndroidManifest.xml" --java "$OUT/gen" \
  --min-sdk-version 26 --target-sdk-version 34 "$OUT/res.zip" || exit 3

echo "--- javac ---"
javac -source 8 -target 8 -nowarn -cp "$ANDROID_JAR" -d "$OUT/c" \
  "$OUT/gen/com/kaoyan/widget/R.java" "$HERE"/src/com/kaoyan/widget/*.java || exit 4

echo "--- d8 ---"
d8 --lib "$ANDROID_JAR" --min-api 26 --output "$OUT/dx" \
  "$OUT"/c/com/kaoyan/widget/*.class || exit 5

echo "--- inject classes.dex ---"
HERE="$HERE" python3 - <<'PY'
import zipfile, shutil, os
here = os.environ['HERE']
out = os.path.join(here, 'out')
base = os.path.join(out, 'base.apk')
dex = os.path.join(out, 'dx', 'classes.dex')
wd = os.path.join(out, 'withdex.apk')
if os.path.exists(wd):
    os.remove(wd)
shutil.copyfile(base, wd)
zf = zipfile.ZipFile(wd, 'a', zipfile.ZIP_DEFLATED)
zf.write(dex, 'classes.dex')
zf.close()
print('injected', os.path.getsize(wd))
PY

echo "--- zipalign ---"
zipalign -f 4 "$OUT/withdex.apk" "$OUT/aligned.apk" || exit 6

echo "--- sign ---"
apksigner sign --ks "$KS" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KEY_PASS" \
  --out "$OUT/app.apk" "$OUT/aligned.apk" || exit 7
apksigner verify --print-certs "$OUT/app.apk" | head -2 || exit 8

ls -l "$OUT/app.apk"
echo "BUILD_DONE"
