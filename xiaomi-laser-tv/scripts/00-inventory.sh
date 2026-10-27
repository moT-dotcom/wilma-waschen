#!/usr/bin/env bash
# Liest Modell, Chipsatz, Pakete und MEMC-Hinweise aus – ändert nichts.
# Aufruf: ./00-inventory.sh 192.168.178.50
source "$(dirname "$0")/lib.sh"
connect "${1:-}"

OUT="$BACKUP_DIR/inventory-$SERIAL_TAG-$(date +%Y%m%d-%H%M%S).txt"

section() { printf '\n===== %s =====\n' "$1"; }

{
  section "Gerät"
  for p in ro.product.manufacturer ro.product.model ro.product.device ro.build.version.release \
           ro.build.display.id ro.board.platform ro.hardware ro.product.cpu.abi; do
    printf '%-28s %s\n' "$p" "$(sh_tv getprop "$p")"
  done

  section "Speicher"
  sh_tv "head -n3 /proc/meminfo"
  sh_tv "df -h /data 2>/dev/null | tail -n1"

  section "Launcher"
  home_launcher

  section "Xiaomi/PatchWall-Pakete"
  installed_packages | grep -iE 'xiaomi|mitv|miui|duokan|patchwall' || echo "(keine)"

  section "Bereits deaktiviert"
  disabled_packages || true

  section "MEMC / Zwischenbild – Systemeigenschaften"
  sh_tv getprop | grep -iE 'memc|frc|mjc|motion|judder|blur' || echo "(nichts gefunden)"

  section "MEMC / Zwischenbild – Einstellungen"
  for ns in global system secure; do
    sh_tv settings list "$ns" | grep -iE 'memc|frc|mjc|motion|judder|picture_mode' | sed "s/^/$ns: /" || true
  done

  section "Bild-/Videodienste"
  sh_tv dumpsys -l | grep -iE 'pq|picture|memc|video|tv_input|hdmi' || true

  section "TV-Eingänge"
  sh_tv dumpsys tv_input | grep -iE 'id=|hdmi' | head -n 20 || true

  section "Alle Pakete ($(installed_packages | wc -l | tr -d ' '))"
  installed_packages
} | tee "$OUT"

echo
ok "Inventur gespeichert: $OUT"
info "Diese Datei kannst du teilen, damit die Paketlisten auf dein Modell zugeschnitten werden."
