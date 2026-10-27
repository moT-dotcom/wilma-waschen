#!/usr/bin/env bash
# Läuft auf dem Raspberry Pi: prüft USB-Grabber, Signal und Hyperion/HyperHDR.
# Aufruf: ./pi-capture-check.sh [/dev/video0]
set -uo pipefail

ok()   { printf '\033[1;32m✔\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m!\033[0m %s\n' "$*"; }
info() { printf '\033[1;36m›\033[0m %s\n' "$*"; }

command -v v4l2-ctl >/dev/null || { warn "v4l2-ctl fehlt: sudo apt install v4l-utils"; exit 1; }

info "Videogeräte:"
v4l2-ctl --list-devices 2>/dev/null

# Ersten USB-Grabber finden, falls kein Gerät übergeben wurde.
DEV="${1:-}"
if [[ -z "$DEV" ]]; then
  DEV="$(v4l2-ctl --list-devices 2>/dev/null | awk '/usb/ {getline; print $1; exit}')"
fi
[[ -n "$DEV" && -e "$DEV" ]] || { warn "Kein USB-Grabber gefunden. Steckt er an einem blauen USB-3-Port?"; exit 1; }
ok "Grabber: $DEV"

info "USB-Geschwindigkeit (5000M = USB 3, 480M = USB 2):"
lsusb -t | grep -iE 'video|uvc' || lsusb -t

info "Unterstützte Formate:"
v4l2-ctl -d "$DEV" --list-formats-ext | grep -E "Pixel Format|Size|Interval" | head -n 30

if command -v fuser >/dev/null && fuser "$DEV" >/dev/null 2>&1; then
  warn "$DEV wird gerade benutzt (vermutlich von Hyperion) – Testbild übersprungen."
elif command -v ffmpeg >/dev/null; then
  OUT="$HOME/grabber-test.jpg"
  if ffmpeg -loglevel error -y -f v4l2 -input_format mjpeg -video_size 1280x720 -i "$DEV" -frames:v 1 "$OUT" 2>/dev/null \
     || ffmpeg -loglevel error -y -f v4l2 -i "$DEV" -frames:v 1 "$OUT" 2>/dev/null; then
    ok "Testbild: $OUT"
    MEAN="$(ffmpeg -loglevel error -i "$OUT" -vf "signalstats,metadata=print:key=lavfi.signalstats.YAVG" -f null - 2>&1 \
            | awk -F= '/YAVG/ {print int($2)}' | head -n1)"
    if [[ -n "$MEAN" && "$MEAN" -lt 20 ]]; then
      warn "Bild ist fast schwarz (Helligkeit $MEAN). Läuft ein Film? Sonst: HDCP – der Splitter gibt kein Bild an den Grabber."
    elif [[ -n "$MEAN" ]]; then
      ok "Signal erkannt (mittlere Helligkeit $MEAN)."
    fi
  else
    warn "Testbild fehlgeschlagen."
  fi
else
  info "ffmpeg nicht installiert – Testbild übersprungen (sudo apt install ffmpeg)."
fi

info "Hyperion/HyperHDR-Dienste:"
systemctl list-units --type=service --all 2>/dev/null | grep -iE 'hyperion|hyperhdr' || warn "Kein Dienst gefunden."

for port in 8090 8080; do
  code="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$port" || true)"
  [[ "$code" == "200" ]] && ok "Weboberfläche: http://$(hostname -I | awk '{print $1}'):$port"
done
