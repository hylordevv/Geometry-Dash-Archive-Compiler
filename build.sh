#!/usr/bin/env bash
# GD Archive Compiler - gera o APK no proprio celular (Alpine do Acode, 32-bit ok).
set -e
cd "$(dirname "$0")"
log() { echo "==> $*"; }
die() { echo "ERRO: $*"; exit 1; }
CACHE="$HOME/.gdac"
mkdir -p "$CACHE"

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/default-jvm}"
export PATH="$JAVA_HOME/bin:$PATH"

find_tool() {
  local t="$1" p
  p="$(command -v "$t" 2>/dev/null || true)"
  if [ -z "$p" ]; then
    p="$(find /usr /opt "$HOME" -type f -name "$t" 2>/dev/null | head -n 1 || true)"
  fi
  echo "$p"
}

# ---------- 1. ferramentas ----------
if command -v apk >/dev/null 2>&1; then
  log "Alpine: instalando utilitarios"
  apk add --no-cache zip unzip curl ca-certificates || true
  for pkg in openjdk17-jdk openjdk11-jdk openjdk8; do
    if command -v javac >/dev/null 2>&1; then break; fi
    log "Alpine: tentando $pkg"
    apk add --no-cache "$pkg" || true
    export PATH="$JAVA_HOME/bin:$PATH"
  done
  if [ -z "$(find_tool aapt2)" ]; then
    log "Alpine: instalando android-build-tools (edge/testing)"
    apk add --no-cache android-build-tools \
      --repository=https://dl-cdn.alpinelinux.org/alpine/edge/testing \
      --repository=https://dl-cdn.alpinelinux.org/alpine/edge/community \
      --repository=https://dl-cdn.alpinelinux.org/alpine/edge/main || true
  fi
elif command -v pkg >/dev/null 2>&1; then
  log "Termux: instalando ferramentas"
  pkg install -y openjdk-17 aapt2 zip unzip curl || true
fi

command -v javac >/dev/null 2>&1 || die "javac nao encontrado. Rode: apk add openjdk8   e me mande o erro."
command -v keytool >/dev/null 2>&1 || die "keytool nao encontrado (JDK nao instalou)."
command -v jarsigner >/dev/null 2>&1 || die "jarsigner nao encontrado (JDK nao instalou)."
command -v zip >/dev/null 2>&1 || die "zip nao encontrado."
log "Java: $(java -version 2>&1 | head -n 1)"

AAPT2="$(find_tool aapt2)"
[ -n "$AAPT2" ] || die "aapt2 nao encontrado."
chmod +x "$AAPT2" 2>/dev/null || true
log "aapt2: $AAPT2"

# ---------- 2. android.jar ----------
ANDROID_JAR="$CACHE/android.jar"
if [ ! -s "$ANDROID_JAR" ]; then
  log "Baixando android.jar"
  for api in 30 29 28; do
    if curl -fL -o "$ANDROID_JAR" "https://github.com/Sable/android-platforms/blob/master/android-$api/android.jar?raw=true"; then break; fi
    rm -f "$ANDROID_JAR"
  done
fi
[ -s "$ANDROID_JAR" ] || die "nao consegui baixar o android.jar."

# ---------- 3. d8 (r8.jar antigo, roda ate no Java 8) ----------
R8="$CACHE/r8.jar"
if [ ! -s "$R8" ]; then
  log "Baixando r8.jar (contem o d8)"
  for v in 4.0.63 3.3.28 3.2.74 3.1.51; do
    if curl -fL -o "$R8" "https://dl.google.com/dl/android/maven2/com/android/tools/r8/$v/r8-$v.jar"; then break; fi
    rm -f "$R8"
  done
fi
[ -s "$R8" ] || die "nao consegui baixar o r8.jar."

# ---------- 4. compilar ----------
rm -rf build out
mkdir -p build/gen build/classes build/dex out

log "[1/5] Recursos"
"$AAPT2" compile --dir res -o build/res.zip

log "[2/5] Manifest"
"$AAPT2" link -o build/base.apk -I "$ANDROID_JAR" \
  --manifest AndroidManifest.xml --java build/gen \
  --min-sdk-version 24 --target-sdk-version 29 build/res.zip

log "[3/5] Java"
javac -source 8 -target 8 -Xlint:-options -nowarn -bootclasspath "$ANDROID_JAR" \
  -d build/classes $(find src build/gen -name '*.java')

log "[4/5] classes.dex"
java -Xmx512m -cp "$R8" com.android.tools.r8.D8 --release --min-api 24 \
  --lib "$ANDROID_JAR" --output build/dex $(find build/classes -name '*.class')
(cd build/dex && zip -q ../base.apk classes.dex)

log "[5/5] Assinar"
if [ ! -f debug.keystore ]; then
  keytool -genkeypair -keystore debug.keystore -storepass android -keypass android \
    -alias gdac -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=GDAC"
fi
jarsigner -keystore debug.keystore -storepass android -keypass android \
  -sigalg SHA256withRSA -digestalg SHA-256 -signedjar out/GDAC.apk build/base.apk gdac

echo
echo "APK pronto: $(pwd)/out/GDAC.apk"
if [ -d /sdcard/Download ] && cp out/GDAC.apk /sdcard/Download/GDAC.apk 2>/dev/null; then
  echo "Copiado para Download/GDAC.apk - abra e instale."
else
  echo "Copie manualmente: cp out/GDAC.apk /sdcard/Download/"
fi
