#!/usr/bin/env bash
# Builds one sideloadable .prg per watch model listed in manifest.xml, into watch/dist/.
#
#   ./build-all.sh                 all models in the manifest
#   ./build-all.sh fenix7 fr265s   only these models
#
# Settings (environment variables, all optional):
#   CIQ_SDK   Connect IQ SDK folder (default: the SDK selected in the SDK Manager)
#   DEV_KEY   developer key, DER format (default: ~/.garmin/developer_key.der)
#   JAVA      java executable (default: java on PATH)
set -euo pipefail
cd "$(dirname "$0")"

if [ -z "${CIQ_SDK:-}" ]; then
    for cfg in "${APPDATA:-}/Garmin/ConnectIQ/current-sdk.cfg" \
               "$HOME/Library/Application Support/Garmin/ConnectIQ/current-sdk.cfg" \
               "$HOME/.Garmin/ConnectIQ/current-sdk.cfg"; do
        if [ -f "$cfg" ]; then
            # The file holds one path, sometimes with a byte-order mark and no newline.
            CIQ_SDK=$(sed 's/^\xEF\xBB\xBF//' "$cfg" | tr -d '\r\n')
            break
        fi
    done
fi
DEV_KEY=${DEV_KEY:-$HOME/.garmin/developer_key.der}
JAVA=${JAVA:-java}

[ -d "${CIQ_SDK:-}" ] || { echo "Connect IQ SDK not found. Set CIQ_SDK to the SDK folder." >&2; exit 1; }
[ -f "$DEV_KEY" ] || { echo "Developer key not found at $DEV_KEY. Set DEV_KEY." >&2; exit 1; }

if [ $# -gt 0 ]; then
    devices="$*"
else
    devices=$(grep -o '<iq:product id="[^"]*"' manifest.xml | sed 's/.*id="//; s/"$//')
fi

mkdir -p dist
failed=""
for d in $devices; do
    printf '%-22s' "$d"
    if out=$("$JAVA" -Xms1g -Dfile.encoding=UTF-8 -jar "$CIQ_SDK/bin/monkeybrains.jar" \
            -o "dist/TidalRemote-$d.prg" -f monkey.jungle -y "$DEV_KEY" -d "$d" -w 2>&1); then
        echo "ok"
    else
        echo "FAILED"
        echo "$out" | tail -5
        failed="$failed $d"
    fi
done
rm -f dist/*.prg.debug.xml dist/*-settings.json dist/*-fit_contributions.json

if [ -n "$failed" ]; then
    echo "Failed:$failed" >&2
    exit 1
fi
echo "Done: watch/dist/"
