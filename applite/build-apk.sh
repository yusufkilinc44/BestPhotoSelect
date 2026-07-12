#!/usr/bin/env bash
# BestPhotoSelect (lite) — AGP'siz APK derleme hattı.
#
# Bu betik, Google'ın Gradle eklentisine (AGP) erişimin olmadığı ortamlar için
# APK'yı doğrudan araçlarla üretir:
#   aapt2 (Ubuntu 'aapt' paketi)  -> kaynak/manifest derleme + R.java
#   kotlinc (Maven Central, kotlin-compiler-embeddable) -> Kotlin derleme
#   D8 (storage.googleapis.com/r8-releases, r8lib.jar) -> classes.dex
#     (dx KULLANILMAZ: invokedynamic/lambda desugar etmediği için cihazda
#      BootstrapMethodError üretir — TFLite ve kotlin-stdlib lambda içerir)
#   zipalign + apksigner (Ubuntu paketleri) -> hizalama + v2/v3 imza
#
# Gerekli girdiler (TOOLS dizininde):
#   kotlin-compiler-embeddable.jar, kotlin-stdlib.jar, annotations.jar,
#   kotlinx-coroutines-core-jvm.jar (derleyicinin kendisi için), trove4j.jar,
#   r8lib.jar (https://storage.googleapis.com/r8-releases/raw/8.3.37/r8lib.jar),
#   tensorflow-lite-{,api-}2.14.0.aar (Maven Central),
#   face_landmark.tflite (https://storage.googleapis.com/mediapipe-assets/)
#   android-34/android.jar (SDK Platform 34)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LITE="$ROOT/applite"
BUILD="$LITE/build"
TOOLS="${TOOLS:-$LITE/tools}"
AJ="${ANDROID_JAR:-/opt/android-sdk/platforms/android-34/android.jar}"
KEYSTORE="$LITE/keystore/bestphoto.keystore"
STOREPASS="${STOREPASS:-bestphoto}"

rm -rf "$BUILD"
mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/apk"

echo "[1/7] aapt2: kaynaklar derleniyor"
aapt2 compile --dir "$ROOT/app/src/main/res" -o "$BUILD/res.zip"

echo "[2/7] aapt2: bağlama (manifest + resources.arsc + R.java)"
aapt2 link \
  -o "$BUILD/base.apk" \
  -I "$AJ" \
  --manifest "$LITE/AndroidManifest.xml" \
  --java "$BUILD/gen" \
  --min-sdk-version 30 \
  --target-sdk-version 34 \
  --version-code "${VERSION_CODE:-1}" \
  --version-name "${VERSION_NAME:-1.0.0}" \
  --auto-add-overlay \
  "$BUILD/res.zip"

echo "[3/7] javac: R sınıfı"
javac -source 8 -target 8 -nowarn -d "$BUILD/classes" \
  "$BUILD/gen/com/bestphotoselect/R.java" 2>/dev/null

echo "[3.5/7] BuildInfo.kt: sürüm etiketi gömülüyor"
cat > "$LITE/src/com/bestphotoselect/lite/BuildInfo.kt" <<EOF
package com.bestphotoselect.lite

/** build-apk.sh tarafından her derlemede üretilir; ekranda görünür sürüm etiketi. */
object BuildInfo {
    const val VERSION_NAME = "${VERSION_NAME:-1.0.0} (${VERSION_CODE:-1})"
}
EOF

echo "[4/7] kotlinc: uygulama kaynakları"
SHARED=(
  "$ROOT/app/src/main/java/com/bestphotoselect/data/model/Models.kt"
  "$ROOT/app/src/main/java/com/bestphotoselect/domain/DHash.kt"
  "$ROOT/app/src/main/java/com/bestphotoselect/domain/PhotoGrouper.kt"
  "$ROOT/app/src/main/java/com/bestphotoselect/domain/QualityScorer.kt"
  "$ROOT/app/src/main/java/com/bestphotoselect/domain/BestPhotoSelector.kt"
  "$ROOT/app/src/main/java/com/bestphotoselect/util/Format.kt"
)
mkdir -p "$BUILD/tflite-rt" "$BUILD/tflite-api"
unzip -oq "$TOOLS/tensorflow-lite.aar" classes.jar -d "$BUILD/tflite-rt"
unzip -oq "$TOOLS/tensorflow-lite-api.aar" classes.jar -d "$BUILD/tflite-api"

java -cp "$TOOLS/kotlin-compiler-embeddable.jar:$TOOLS/kotlin-stdlib.jar:$TOOLS/annotations.jar:$TOOLS/coroutines-core.jar:$TOOLS/trove4j.jar" \
  org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -classpath "$AJ:$TOOLS/kotlin-stdlib.jar:$BUILD/classes:$BUILD/tflite-rt/classes.jar:$BUILD/tflite-api/classes.jar" \
  -d "$BUILD/classes" \
  -jvm-target 1.8 \
  -Xlambdas=class -Xsam-conversions=class \
  -no-stdlib -no-reflect -nowarn \
  "${SHARED[@]}" \
  $(find "$LITE/src" -name '*.kt')

echo "[5/7] D8: classes.dex (lambda desugaring dahil)"
(cd "$BUILD/classes" && jar -cf "$BUILD/app-classes.jar" .)
java -cp "$TOOLS/r8lib.jar" com.android.tools.r8.D8 \
  --release \
  --min-api 30 \
  --lib "$AJ" \
  --output "$BUILD/apk" \
  "$BUILD/app-classes.jar" \
  "$TOOLS/kotlin-stdlib.jar" \
  "$BUILD/tflite-rt/classes.jar" "$BUILD/tflite-api/classes.jar"

echo "[6/7] APK birleştirme (dex + model + jni)"
cp "$BUILD/base.apk" "$BUILD/unsigned.apk"
mkdir -p "$BUILD/apk/assets" "$BUILD/apk/lib"
cp "$TOOLS/face_landmark.tflite" "$BUILD/apk/assets/"
for abi in arm64-v8a armeabi-v7a; do
  mkdir -p "$BUILD/apk/lib/$abi"
  unzip -oqj "$TOOLS/tensorflow-lite.aar" "jni/$abi/*.so" -d "$BUILD/apk/lib/$abi"
done
(cd "$BUILD/apk" && zip -qr "$BUILD/unsigned.apk" classes.dex assets lib)

echo "[7/7] zipalign + apksigner"
zipalign -f 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"
if [ ! -f "$KEYSTORE" ]; then
  mkdir -p "$(dirname "$KEYSTORE")"
  keytool -genkeypair -keystore "$KEYSTORE" -storepass "$STOREPASS" -keypass "$STOREPASS" \
    -alias bestphotoselect -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=BestPhotoSelect"
fi
apksigner sign \
  --ks "$KEYSTORE" --ks-pass "pass:$STOREPASS" --key-pass "pass:$STOREPASS" \
  --out "$BUILD/BestPhotoSelect.apk" "$BUILD/aligned.apk"
apksigner verify "$BUILD/BestPhotoSelect.apk"

echo "APK hazır: $BUILD/BestPhotoSelect.apk"
