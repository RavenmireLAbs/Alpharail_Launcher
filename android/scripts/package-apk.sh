#!/usr/bin/env bash
# Fallback for Linux x86-64 builders that cannot install the Android SDK.
set -euo pipefail
umask 077
source_dir=$(cd -- "$(dirname -- "$0")/../app/src/main" && pwd)
gradle_file="$source_dir/../../build.gradle"
version_name=$(sed -n "s/^[[:space:]]*versionName '\([0-9][0-9.]*\)'.*/\1/p" "$gradle_file")
version_code=$(sed -n 's/^[[:space:]]*versionCode \([0-9][0-9]*\).*/\1/p' "$gradle_file")
[[ "$version_name" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ && "$version_code" =~ ^[1-9][0-9]*$ ]]

output=$(realpath -m "${1:?Provide an output APK path}")
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cd "$work"
fetch() {
  curl -H "Accept: application/vnd.github.raw+json" --fail --silent --show-error --location --retry 3 --retry-all-errors --max-time 90 "$1" -o "$2" || return 1
  printf '%s  %s\n' "$3" "$2" | sha256sum --check
}
fetch https://registry.npmjs.org/aaptjs3/-/aaptjs3-2.0.2.tgz aapt.tgz 26031bd1acb577edce91675ae92daf63cf85795d4e2399333edeb994dd9197e9
fetch https://registry.npmjs.org/@drxiaozhi/minapk/-/minapk-0.5.1.tgz tools.tgz 5e411bfdf4a0eae820d34afb9727e0675dcb3cc04608f602ee427c2b949c9e65
fetch https://raw.githubusercontent.com/Sable/android-platforms/1e98db1a199e8f7f85541af26bfc27019501b132/android-35/android.jar android.jar 4566663c3876e022b4fa4ced8c8697c4ab1688267f090114fd92d027b32e619b || \
  fetch https://api.github.com/repos/Sable/android-platforms/contents/android-35/android.jar?ref=1e98db1a199e8f7f85541af26bfc27019501b132 android.jar 4566663c3876e022b4fa4ced8c8697c4ab1688267f090114fd92d027b32e619b
tar xzf aapt.tgz package/bin/x64/linux/aapt2
chmod +x package/bin/x64/linux/aapt2
tar xzf tools.tgz package/tools/d8.jar package/tools/apksigner.jar package/tools/ecj-3.45.0.jar
mkdir classes dex
mapfile -t sources < <(find "$source_dir/java" -name '*.java' | sort)
java -jar package/tools/ecj-3.45.0.jar -8 -encoding UTF-8 -proc:none -classpath android.jar -d classes "${sources[@]}"
python3 - <<'PYZIP'
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
with ZipFile('classes.jar', 'w', ZIP_DEFLATED) as jar:
    for path in sorted(Path('classes').rglob('*.class')):
        jar.write(path, path.relative_to('classes'))
PYZIP
java -cp package/tools/d8.jar com.android.tools.r8.D8 --release --min-api 26 --lib android.jar --output dex classes.jar
sed 's/<manifest /<manifest package="com.ravenmire.alpharail" /' "$source_dir/AndroidManifest.xml" > AndroidManifest.xml
package/bin/x64/linux/aapt2 compile --dir "$source_dir/res" -o resources.zip
package/bin/x64/linux/aapt2 link -I android.jar --manifest AndroidManifest.xml --min-sdk-version 26 --target-sdk-version 34 --version-code "$version_code" --version-name "$version_name" -o unsigned.apk resources.zip
python3 - <<'PY'
from zipfile import ZipFile, ZIP_DEFLATED
with ZipFile('unsigned.apk', 'a', ZIP_DEFLATED) as apk:
    apk.write('dex/classes.dex', 'classes.dex')
PY
if [[ -z ${STILL_KEYSTORE:-} ]]; then
  export STILL_STORE_PASSWORD=$(openssl rand -hex 32)
  openssl req -x509 -newkey rsa:3072 -nodes -keyout signing.key -out signing.crt -days 3650 -subj '/CN=Still development build' 2>/dev/null
  openssl pkcs12 -export -in signing.crt -inkey signing.key -name still -out development.p12 -passout env:STILL_STORE_PASSWORD
  STILL_KEYSTORE="$work/development.p12"
fi
mkdir -p "$(dirname -- "$output")"
java -jar package/tools/apksigner.jar sign --ks "$STILL_KEYSTORE" --ks-key-alias still --ks-pass env:STILL_STORE_PASSWORD --key-pass env:STILL_STORE_PASSWORD --out "$output" unsigned.apk
java -jar package/tools/apksigner.jar verify --verbose --print-certs "$output"
package/bin/x64/linux/aapt2 dump badging "$output" | sed -n '1,8p'
