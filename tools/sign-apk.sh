#!/bin/sh
set -eu
: "${TOMRUN_KEYSTORE:?Provide the retained keystore path}"
: "${TOMRUN_KEYSTORE_PASSWORD:?Provide the retained key password}"
if [ "$#" -ne 3 ]; then
  echo "Usage: sign-apk.sh apksigner.jar input.apk output.apk" >&2
  exit 2
fi
java -jar "$1" sign --ks "$TOMRUN_KEYSTORE" --ks-key-alias tomrunpilot --ks-pass env:TOMRUN_KEYSTORE_PASSWORD --key-pass env:TOMRUN_KEYSTORE_PASSWORD --out "$3" "$2"
java -jar "$1" verify --print-certs "$3"
