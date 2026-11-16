#!/usr/bin/env bash
# Prüft, ob der Beamer das laufende Bild an Aufnahmen herausgibt – die
# Voraussetzung für den Bild-Sync (Ambilight) der Kino-Licht-App.
# Vorher am Beamer den Apple-TV-Eingang wählen und etwas Helles abspielen.
# Aufruf: ./50-capture-probe.sh 192.168.178.50
source "$(dirname "$0")/lib.sh"
connect "${1:-}"

RAW="$BACKUP_DIR/probe-$SERIAL_TAG.raw"
PNG="$BACKUP_DIR/probe-$SERIAL_TAG.png"

info "Chipsatz: $(sh_tv getprop ro.board.platform) / $(sh_tv getprop ro.hardware), Android $(sh_tv getprop ro.build.version.release)"
info "Vordergrund: $(sh_tv dumpsys window 2>/dev/null | grep -m1 -E 'mCurrentFocus|mFocusedApp' | sed 's/^ *//')"

echo
info "1) Bildschirmfoto wie eine Aufnahme-App (dieselbe Quelle wie der Bild-Sync) …"
adb -s "$SERIAL" exec-out screencap > "$RAW" || die "screencap fehlgeschlagen"
adb -s "$SERIAL" exec-out screencap -p > "$PNG" || true
[[ -s "$RAW" ]] || die "Leeres Bildschirmfoto – die Firmware sperrt screencap."

VERDICT=unknown
if command -v python3 >/dev/null; then
  # Rohformat: Breite, Höhe, Pixelformat (je 4 Byte), ab Android 9 zusätzlich Farbraum, dann RGBA.
  VERDICT="$(python3 - "$RAW" <<'PY'
import struct, sys
data = open(sys.argv[1], "rb").read()
w, h, _ = struct.unpack_from("<III", data, 0)
header = len(data) - w * h * 4
if w == 0 or h == 0 or header not in (12, 16):
    print("unknown")
    sys.exit()
step = max(1, (w * h) // 20000)
total = count = bright = 0
for i in range(0, w * h, step):
    o = header + i * 4
    luma = (data[o] * 299 + data[o + 1] * 587 + data[o + 2] * 114) // 1000
    total += luma
    count += 1
    bright += luma > 16
print(f"{w}x{h} {total // count} {100 * bright // count}")
PY
)"
fi

if [[ "$VERDICT" == "unknown" ]]; then
  warn "Automatische Auswertung nicht möglich (python3 fehlt oder unbekanntes Format)."
  info "Bitte selbst ansehen: $PNG – ist der Film zu sehen oder ist es schwarz?"
else
  read -r size mean bright <<<"$VERDICT"
  info "Bildschirmfoto $size, mittlere Helligkeit $mean/255, $bright % der Fläche nicht schwarz"
  if (( bright >= 20 )); then
    ok "Das Bild ist sichtbar: Der Bild-Sync sollte mit dieser Quelle funktionieren."
  elif (( bright >= 2 )); then
    warn "Nur ein kleiner Teil ist sichtbar (vermutlich Einblendungen über schwarzem Video)."
    warn "Das Videobild selbst gibt Android nicht heraus – der Bild-Sync bliebe hier dunkel."
  else
    warn "Schwarz: Android gibt dieses Bild nicht an Aufnahmen heraus."
    warn "Der Bild-Sync funktioniert dann nur mit Apps auf dem Beamer selbst."
  fi
fi
info "Zum Nachsehen gespeichert: $PNG"

echo
info "2) Hardware-Videoaufnahme des Chips (Amlogic amvideocap) …"
CAP="$(sh_tv 'ls -l /dev/amvideocap0 2>&1')"
if grep -q "No such file" <<<"$CAP"; then
  info "Nicht vorhanden – dieser Beamer hat keine solche Schnittstelle."
else
  info "$CAP"
  if sh_tv 'dd if=/dev/amvideocap0 of=/dev/null bs=4096 count=1 2>&1' | grep -qiE 'denied|not permitted'; then
    info "Vorhanden, aber für Apps und ADB gesperrt (nur mit Root nutzbar)."
  else
    ok "Vorhanden und per ADB lesbar – darüber ließe sich auch das HDMI-Bild abgreifen. Bitte diese Ausgabe teilen."
  fi
fi
