#!/usr/bin/env bash
# Installiert die Kino-Licht-App auf dem Beamer und richtet sie für Dauerbetrieb ein.
# Aufruf:
#   ./40-install-kino-licht.sh 192.168.178.50              # neueste Version von GitHub laden
#   ./40-install-kino-licht.sh 192.168.178.50 kino-licht.apk  # lokale APK verwenden
source "$(dirname "$0")/lib.sh"
connect "${1:-}"

PKG="de.kinolicht"
APK="${2:-}"
RELEASE_URL="https://github.com/moT-dotcom/wilma-waschen/releases/download/kino-licht/kino-licht.apk"

if [[ -z "$APK" ]]; then
  APK="$BACKUP_DIR/kino-licht.apk"
  info "Lade neueste Version …"
  curl -fsSL -o "$APK" "$RELEASE_URL" \
    || die "Download fehlgeschlagen. APK manuell unter github.com/moT-dotcom/wilma-waschen/releases laden und als zweiten Parameter angeben."
fi
[[ -f "$APK" ]] || die "APK nicht gefunden: $APK"

info "Installiere …"
adb -s "$SERIAL" install -r "$APK" | tr -d '\r' | grep -q Success || die "Installation fehlgeschlagen."
ok "Kino-Licht installiert."

# Damit Zeitpläne und Beamer-Kopplung auch im Standby zuverlässig laufen:
sh_tv dumpsys deviceidle whitelist +$PKG >/dev/null 2>&1 && ok "Von der Energiesparsteuerung ausgenommen." || true
sh_tv appops set $PKG SCHEDULE_EXACT_ALARM allow >/dev/null 2>&1 || true
sh_tv appops set $PKG RUN_IN_BACKGROUND allow >/dev/null 2>&1 || true
sh_tv appops set $PKG RUN_ANY_IN_BACKGROUND allow >/dev/null 2>&1 || true

sh_tv am start -n "$PKG/de.kinolicht.app.ui.MainActivity" >/dev/null
ok "App gestartet – jetzt am Beamer unter „Verbindung“ auf „Im Netzwerk suchen“ drücken."
