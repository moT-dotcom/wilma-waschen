#!/usr/bin/env bash
# Installiert "Hyperion Grabber" (Android) auf dem Beamer.
# Erfasst nur Apps, die AUF dem Beamer laufen und kein DRM nutzen
# (z. B. YouTube, Mediaplayer). Das HDMI-Bild vom Apple TV sieht die App nicht.
# Aufruf: ./30-install-grabber.sh 192.168.178.50
source "$(dirname "$0")/lib.sh"
connect "${1:-}"

REPO="abrenoch/hyperion-android-grabber"
APK="$BACKUP_DIR/hyperion-grabber.apk"

info "Neueste Version von $REPO suchen …"
URL="$(curl -fsSL "https://api.github.com/repos/$REPO/releases/latest" \
  | grep -oE '"browser_download_url": *"[^"]+\.apk"' | head -n1 | cut -d'"' -f4)" || true
[[ -n "$URL" ]] || die "Keine APK gefunden – manuell laden: https://github.com/$REPO/releases"

curl -fsSL -o "$APK" "$URL" || die "Download fehlgeschlagen: $URL"
ok "Geladen: ${URL##*/}"

adb -s "$SERIAL" install -r "$APK" | tr -d '\r' | grep -q Success \
  || die "Installation fehlgeschlagen – Ausgabe von 'adb install' prüfen."
ok "Hyperion Grabber installiert."
info "In der App: IP des Pi und Port 19445 (Protobuf-Server, in Hyperion/HyperHDR aktivieren) eintragen, Aufnahme starten und Bildschirmaufnahme erlauben."
