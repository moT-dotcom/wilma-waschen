#!/usr/bin/env bash
# Prüft, ob sich dieser Beamer rooten lässt – und ändert dabei NICHTS.
# Rooten selbst muss gerätespezifisch und von Hand gemacht werden; für den
# Mi Laser Projector 150 (MJJGYY02FM) ist bisher kein Root bekannt. Dieses
# Skript sammelt nur die Fakten, mit denen man das für dein Gerät entscheidet.
# Aufruf: ./60-root-check.sh 192.168.178.50
source "$(dirname "$0")/lib.sh"
connect "${1:-}"

OUT="$BACKUP_DIR/root-check-$SERIAL_TAG-$(date +%Y%m%d-%H%M%S).txt"
yes_no() { [[ -n "$1" ]] && echo "$1" || echo "(leer)"; }

{
  echo "===== Gerät ====="
  for p in ro.product.manufacturer ro.product.model ro.product.device ro.board.platform \
           ro.hardware ro.build.version.release ro.build.type ro.build.tags ro.build.fingerprint; do
    printf '%-30s %s\n' "$p" "$(sh_tv getprop "$p")"
  done

  echo
  echo "===== Schon gerootet? ====="
  SU="$(sh_tv 'command -v su magisk 2>/dev/null')"
  if [[ -n "$SU" ]]; then
    echo "su/magisk gefunden: $SU  → sieht nach vorhandenem Root aus."
  else
    echo "Kein su/magisk im Pfad."
  fi
  echo "adb-Rechte jetzt: $(sh_tv id)"
  # 'adb root' geht nur bei userdebug/eng-Builds; bei Verkaufsgeräten (user) nie.
  echo "adb root: $(adb -s "$SERIAL" root 2>&1 | tr -d '\r' | head -n1)"
  adb -s "$SERIAL" wait-for-device 2>/dev/null || true

  echo
  echo "===== Bootloader / OEM-Unlock ====="
  # Wichtigster Punkt: Ohne entsperrbaren Bootloader kein dauerhafter Root.
  printf '%-30s %s\n' "ro.boot.verifiedbootstate" "$(yes_no "$(sh_tv getprop ro.boot.verifiedbootstate)")"
  printf '%-30s %s\n' "ro.boot.vbmeta.device_state" "$(yes_no "$(sh_tv getprop ro.boot.vbmeta.device_state)")"
  printf '%-30s %s\n' "ro.secure" "$(sh_tv getprop ro.secure)"
  printf '%-30s %s\n' "ro.oem_unlock_supported" "$(yes_no "$(sh_tv getprop ro.oem_unlock_supported)")"
  printf '%-30s %s\n' "sys.oem_unlock_allowed" "$(yes_no "$(sh_tv getprop sys.oem_unlock_allowed)")"
  echo "Flag im System (0=gesperrt,1=erlaubt): $(sh_tv 'settings get global oem_unlock_allowed 2>/dev/null')"
  echo "'OEM-Entsperrung' sichtbar? → am Beamer unter Entwickleroptionen nachsehen."

  echo
  echo "===== Partitionen (für ein eigenes boot.img nötig) ====="
  sh_tv 'ls -l /dev/block/by-name 2>/dev/null || ls -l /dev/block/platform/*/by-name 2>/dev/null' | grep -iE 'boot|recovery|logo|misc' | head -n 20
  echo "(Lesen der boot-Partition ohne Root ist normalerweise gesperrt – nur zur Übersicht.)"

  echo
  echo "===== Recovery / fastboot ====="
  echo "Ein echtes fastboot (nötig für 'fastboot oem unlock') prüfst du am Gerät:"
  echo "  adb -s $SERIAL reboot bootloader   und dann am Rechner:  fastboot devices"
  echo "Bei vielen Xiaomi-Beamern landet man dabei NUR in der Recovery, nicht in fastboot."

  echo
  echo "===== Amlogic Burn-Mode (USB Burning Tool) ====="
  echo "Amlogic-SoC ($(sh_tv getprop ro.board.platform)): Firmware lässt sich per"
  echo "USB Burning Tool (nur Windows) über ein USB-A-zu-A-Kabel neu flashen."
  echo "Das ist der übliche Rettungsweg, falls etwas schiefgeht – Original-Firmware vorher sichern."
} | tee "$OUT"

echo
ok "Bericht gespeichert: $OUT"
cat <<'HELP'

Nächste Schritte – nüchtern:
  1) Teile diesen Bericht. Entscheidend ist, ob "OEM-Entsperrung" vorhanden und
     vbmeta/verifiedbootstate entsperrbar sind.
  2) Ist der Bootloader NICHT entsperrbar (häufig bei diesem Modell), gibt es
     ohne eine gerätespezifische Lücke keinen gefahrlosen Root. Dann lohnt der
     Blick in den XDA-Thread zu Xiaomi/Fengmi/Wemax-Projektoren, ob jemand für
     genau deine Firmware einen Weg gefunden hat.
  3) Ist er entsperrbar: Original-boot.img besorgen, mit Magisk patchen, per
     fastboot flashen. Jeder Schritt ist hier gerätespezifisch und kann den
     Beamer vorübergehend unbrauchbar machen – nur mit gesicherter Firmware und
     bereitliegendem USB Burning Tool.

Für den Bild-Sync bzw. Ambilight brauchst du Root übrigens nur, wenn
50-capture-probe.sh "schwarz" meldet UND /dev/amvideocap0 vorhanden, aber
gesperrt ist. Sonst bringt Rooten dafür nichts.
HELP
