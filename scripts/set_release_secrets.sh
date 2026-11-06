#!/bin/sh
# One-time: uploads the release signing key (~/.android/wordly-release.jks + keystore.properties,
# both kept out of git) as GitHub Actions secrets for the Release workflow.
set -e
cd "$(dirname "$0")/.."
PASS=$(grep '^storePassword=' keystore.properties | cut -d= -f2-)
ALIAS=$(grep '^keyAlias=' keystore.properties | cut -d= -f2-)
STORE=$(grep '^storeFile=' keystore.properties | cut -d= -f2-)
base64 -i "$STORE" | gh secret set WORDLY_KEYSTORE_BASE64
printf '%s' "$PASS" | gh secret set WORDLY_KEYSTORE_PASSWORD
printf '%s' "$ALIAS" | gh secret set WORDLY_KEY_ALIAS
printf '%s' "$PASS" | gh secret set WORDLY_KEY_PASSWORD
gh secret list
