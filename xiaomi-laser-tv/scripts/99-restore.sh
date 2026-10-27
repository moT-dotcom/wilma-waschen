#!/usr/bin/env bash
# Macht Debloat und Performance-Tuning rückgängig.
# Aufruf:
#   ./99-restore.sh 192.168.178.50              # alles zurück
#   ./99-restore.sh 192.168.178.50 com.foo.bar  # nur ein Paket zurück
source "$(dirname "$0")/lib.sh"
connect "${1:-}"
ONLY="${2:-}"

LOG="$BACKUP_DIR/changes-$SERIAL_TAG.log"
SETTINGS_BACKUP="$BACKUP_DIR/settings-$SERIAL_TAG.txt"

restore_pkg() {
  local action="$1" pkg="$2" out
  case "$action" in
    uninstalled) out="$(sh_tv "cmd package install-existing $pkg 2>&1")" ;;
    *)           out="$(sh_tv "pm enable $pkg 2>&1")" ;;
  esac
  if grep -qiE 'error|exception|unknown|not found' <<<"$out"; then
    warn "fehlgeschlagen: $pkg ($out)"
  else
    ok "wiederhergestellt: $pkg"
  fi
}

if [[ -n "$ONLY" ]]; then
  action="$(awk -F'\t' -v p="$ONLY" '$2 == p {a = $1} END {print a}' "$LOG" 2>/dev/null || true)"
  restore_pkg "${action:-disabled}" "$ONLY"
  [[ -f "$LOG" ]] && awk -F'\t' -v p="$ONLY" '$2 != p' "$LOG" > "$LOG.tmp" && mv "$LOG.tmp" "$LOG"
  exit 0
fi

if [[ -f "$LOG" ]]; then
  while IFS=$'\t' read -r action pkg _; do
    restore_pkg "$action" "$pkg"
  done < "$LOG"
  mv "$LOG" "$LOG.restored-$(date +%Y%m%d-%H%M%S)"
else
  info "Keine Paketänderungen protokolliert."
fi

if [[ -f "$SETTINGS_BACKUP" ]]; then
  while read -r ns key old; do
    if [[ "$old" == "null" ]]; then
      sh_tv settings delete "$ns" "$key" >/dev/null
    else
      sh_tv settings put "$ns" "$key" "$old"
    fi
    ok "$key → $old"
  done < "$SETTINGS_BACKUP"
  mv "$SETTINGS_BACKUP" "$SETTINGS_BACKUP.restored-$(date +%Y%m%d-%H%M%S)"
fi

info "Neustart empfohlen: adb -s $SERIAL reboot"
