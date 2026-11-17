#!/usr/bin/env bash
# Ein-Klick-Optimierung: führt Inventur, Entschlacken und Performance-Tuning
# in der richtigen Reihenfolge aus und zeigt am Ende die Kino-Einstellungen.
# Läuft ohne Root, alles umkehrbar mit 99-restore.sh.
#
# Aufruf:
#   ./01-optimize-all.sh 192.168.178.50            # Probelauf: zeigt nur, was passieren würde
#   ./01-optimize-all.sh 192.168.178.50 --apply    # wirklich ausführen (Profile safe + xiaomi)
#   ./01-optimize-all.sh 192.168.178.50 --apply optional   # zusätzlich optionale Apps
source "$(dirname "$0")/lib.sh"
HERE="$(cd "$(dirname "$0")" && pwd)"

TARGET="${1:-}"; shift || true
APPLY=0
EXTRA=()
for arg in "$@"; do
  case "$arg" in
    --apply) APPLY=1 ;;
    *) EXTRA+=("$arg") ;;
  esac
done

# Verbinden nur zur Vorab-Prüfung; die Teilskripte verbinden selbst erneut.
connect "$TARGET"
MODEL="$(sh_tv getprop ro.product.model)"
echo
info "════════════════════════════════════════════════════════"
info " Kino-Optimierung für $MODEL ($SERIAL)"
info "════════════════════════════════════════════════════════"
if [[ $APPLY -eq 0 ]]; then
  warn "PROBELAUF – es wird nichts verändert. Zum Ausführen --apply anhängen."
fi
echo

step() { echo; info "── $1"; }

step "1/4  Inventur (liest nur aus)"
"$HERE/00-inventory.sh" "$TARGET" >/dev/null && ok "Inventur gespeichert unter backup/."

step "2/4  Entschlacken (Werbung, Telemetrie, ungenutzte Apps)"
if [[ $APPLY -eq 1 ]]; then
  "$HERE/10-debloat.sh" "$TARGET" --apply safe xiaomi "${EXTRA[@]}"
else
  "$HERE/10-debloat.sh" "$TARGET" safe xiaomi "${EXTRA[@]}"
fi

step "3/4  Performance (Animationen, Hintergrund-Scans, CEC, dexopt)"
if [[ $APPLY -eq 1 ]]; then
  "$HERE/20-performance.sh" "$TARGET"
else
  info "Übersprungen im Probelauf (setzt Systemwerte). Mit --apply wird es ausgeführt."
fi

step "4/4  Kino-Einstellungen"
cat <<'CINEMA'
Diese Punkte stellt kein Skript ein – sie liegen im Bildmenü des Beamers und
am Apple TV. Die beste kinoähnliche Wiedergabe:

APPLE TV  (Einstellungen → Video und Audio)
  • Format .................... 4K SDR 60 Hz
  • Dynamikbereich anpassen ... Ein   (schaltet pro Film auf HDR)
  • BILDRATE ANPASSEN ......... Ein   ← der wichtigste Schalter: 24p ohne Ruckeln
  • Chroma .................... 4:2:2

BEAMER  (Bild → Erweitert)
  • Bildmodus ................. Film / Kino / Movie
  • Farbtemperatur ........... Warm / Warm2  (Richtung 6500 K / D65)
  • Gamma ..................... 2.2  (abgedunkelter Raum: 2.4)
  • MEMC / Bewegung ........... Niedrig für Filme, Aus im Spielmodus
  • Rauschunterdrückung ...... Aus  (frisst Filmkorn und Details)
  • Dynamischer Kontrast ..... Aus  (hält die Helligkeit stabil)
  • Local Dimming / Laser .... hoch für HDR, niedriger im dunklen Raum
  • Schärfe ................... 0 bzw. neutral  (höhere Werte = Kantenartefakte)
  • HDMI-Modus/Eingang ....... „Erweitert“ bzw. 2.0, sonst kein 4K60 HDR

Vollständige Erklärung: xiaomi-laser-tv/index.html, Abschnitt „Bild & Zwischenbild“.
CINEMA

echo
if [[ $APPLY -eq 1 ]]; then
  ok "Fertig. Jetzt den Beamer einmal neu starten:  adb -s $SERIAL reboot"
  info "Rückgängig machen jederzeit mit:  ./99-restore.sh $TARGET"
else
  info "Das war der Probelauf. Wenn es passt, nochmal mit --apply ausführen."
fi
