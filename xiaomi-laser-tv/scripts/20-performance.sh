#!/usr/bin/env bash
# Performance-Tuning ohne Root. Alte Werte werden gesichert (99-restore.sh).
# Aufruf: ./20-performance.sh 192.168.178.50
source "$(dirname "$0")/lib.sh"
connect "${1:-}"

SETTINGS_BACKUP="$BACKUP_DIR/settings-$SERIAL_TAG.txt"

# namespace  schlüssel  neuer-wert  kommentar
TWEAKS=(
  "global window_animation_scale 0.5 Menüs reagieren schneller"
  "global transition_animation_scale 0.5 kürzere Übergänge"
  "global animator_duration_scale 0.5 kürzere Animationen"
  "global wifi_scan_always_enabled 0 kein WLAN-Scan im Hintergrund"
  "global ble_scan_always_enabled 0 kein Bluetooth-Scan im Hintergrund"
  "global hdmi_control_enabled 1 HDMI-CEC an (Apple TV schaltet Beamer)"
  "global hdmi_control_auto_wakeup_enabled 1 Beamer wacht mit Apple TV auf"
  "global hdmi_control_auto_device_off_enabled 1 Apple TV geht mit Beamer aus"
)

for t in "${TWEAKS[@]}"; do
  read -r ns key value comment <<<"$t"
  old="$(sh_tv settings get "$ns" "$key")"
  # Nur den allerersten Originalwert sichern, nicht bei jedem Lauf überschreiben.
  grep -q "^$ns $key " "$SETTINGS_BACKUP" 2>/dev/null || echo "$ns $key $old" >> "$SETTINGS_BACKUP"
  sh_tv settings put "$ns" "$key" "$value"
  ok "$key: $old → $value  ($comment)"
done

info "Caches leeren …"
sh_tv pm trim-caches 64G >/dev/null || true

info "Apps neu optimieren (dex2oat) – dauert einige Minuten, Beamer nicht ausschalten …"
if sh_tv cmd package bg-dexopt-job >/dev/null 2>&1; then
  ok "Optimierung abgeschlossen."
else
  warn "bg-dexopt-job nicht verfügbar – übersprungen."
fi

ok "Fertig. Alte Werte: $SETTINGS_BACKUP"
info "Empfohlen: Neustart mit  adb -s $SERIAL reboot"
