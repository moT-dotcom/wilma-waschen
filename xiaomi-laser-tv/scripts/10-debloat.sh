#!/usr/bin/env bash
# Deaktiviert Bloatware nach Profil. Standard ist ein Probelauf ohne Änderungen.
#
# Aufruf:
#   ./10-debloat.sh 192.168.178.50                       # Probelauf: safe + xiaomi
#   ./10-debloat.sh 192.168.178.50 --apply               # wirklich ausführen
#   ./10-debloat.sh 192.168.178.50 --apply safe optional # eigene Profilauswahl
#
# Jede Änderung wird in backup/changes-<gerät>.log protokolliert und lässt
# sich mit 99-restore.sh rückgängig machen.
source "$(dirname "$0")/lib.sh"

TARGET="${1:-}"; shift || true
APPLY=0
PROFILES=()
for arg in "$@"; do
  case "$arg" in
    --apply) APPLY=1 ;;
    *) PROFILES+=("$arg") ;;
  esac
done
[[ ${#PROFILES[@]} -gt 0 ]] || PROFILES=(safe xiaomi)

connect "$TARGET"
HOME_LAUNCHER="$(home_launcher)"
info "Aktiver Launcher (geschützt): ${HOME_LAUNCHER:-unbekannt}"
info "Profile: ${PROFILES[*]}"

INSTALLED="$(installed_packages)"
DISABLED="$(disabled_packages)"
LOG="$BACKUP_DIR/changes-$SERIAL_TAG.log"

if [[ $APPLY -eq 1 && ! -f "$BACKUP_DIR/packages-before-$SERIAL_TAG.txt" ]]; then
  printf '%s\n' "$INSTALLED" > "$BACKUP_DIR/packages-before-$SERIAL_TAG.txt"
  ok "Ausgangszustand gesichert."
fi

todo=0 done_count=0 failed=0
for profile in "${PROFILES[@]}"; do
  echo
  info "── Profil: $profile"
  while read -r pkg; do
    if ! grep -qxF "$pkg" <<<"$INSTALLED"; then
      continue
    elif is_protected "$pkg"; then
      warn "geschützt, übersprungen: $pkg"
    elif grep -qxF "$pkg" <<<"$DISABLED"; then
      printf '  = %s (bereits deaktiviert)\n' "$pkg"
    elif [[ $APPLY -eq 0 ]]; then
      printf '  - %s\n' "$pkg"
      todo=$((todo + 1))
    else
      # Erst "deaktivieren" versuchen, sonst für Benutzer 0 entfernen.
      # Beides ist ohne Root und ohne Werksreset umkehrbar.
      if sh_tv "pm disable-user --user 0 $pkg 2>&1" | grep -q 'disabled'; then
        printf 'disabled\t%s\t%s\n' "$pkg" "$(date -Iseconds)" >> "$LOG"
        ok "deaktiviert: $pkg"
        done_count=$((done_count + 1))
      elif sh_tv "pm uninstall -k --user 0 $pkg 2>&1" | grep -q 'Success'; then
        printf 'uninstalled\t%s\t%s\n' "$pkg" "$(date -Iseconds)" >> "$LOG"
        ok "entfernt (user 0): $pkg"
        done_count=$((done_count + 1))
      else
        warn "fehlgeschlagen: $pkg"
        failed=$((failed + 1))
      fi
    fi
  done < <(load_list "$profile")
done

echo
if [[ $APPLY -eq 0 ]]; then
  info "Probelauf: $todo Pakete würden deaktiviert. Mit --apply ausführen."
else
  ok "$done_count deaktiviert, $failed fehlgeschlagen. Protokoll: $LOG"
  info "Jetzt den Beamer einmal neu starten: adb -s $SERIAL reboot"
fi
