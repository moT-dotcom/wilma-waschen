#!/usr/bin/env bash
# Gemeinsame Helfer für alle Beamer-Skripte. Wird per `source` eingebunden.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
PKG_DIR="$ROOT_DIR/packages"
BACKUP_DIR="$ROOT_DIR/backup"
mkdir -p "$BACKUP_DIR"

# Pakete, die niemals angefasst werden – egal was in den Listen steht.
# Schützt System-UI, Einstellungen, Google-Dienste, HDMI/TV-Eingänge,
# Bildverarbeitung, Fernbedienung, Tastatur und den Xiaomi-Launcher.
PROTECTED_REGEX='^(android|com\.android\.(systemui|settings|tv\.settings|providers\..*|shell|phone|bluetooth|inputdevices|externalstorage|packageinstaller|permissioncontroller|vending|tv)|com\.google\.android\.(gms|gsf|tvlauncher|leanbacklauncher|tungsten\..*|permissioncontroller|ext\..*|packageinstaller)|.*(tvinput|hdmi|tvserver|tvservice|picture|inputmethod|keyboard|remote|rcu|bluetooth|mitv\.tvhome).*)$'

info() { printf '\033[1;36m›\033[0m %s\n' "$*"; }
ok()   { printf '\033[1;32m✔\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m!\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31m✘\033[0m %s\n' "$*" >&2; exit 1; }

# Verbindet per Netzwerk-ADB. Erwartet IP (optional mit :Port) oder $TV_IP.
connect() {
  local target="${1:-${TV_IP:-}}"
  [[ -n "$target" ]] || die "Bitte IP des Beamers angeben, z. B.: $0 192.168.178.50"
  command -v adb >/dev/null || die "adb nicht gefunden – Android Platform-Tools installieren."
  [[ "$target" == *:* ]] || target="$target:5555"
  adb connect "$target" >/dev/null 2>&1 || true
  SERIAL="$target"
  local state
  state="$(adb -s "$SERIAL" get-state 2>/dev/null || true)"
  [[ "$state" == "device" ]] || die "Keine Verbindung zu $SERIAL (${state:-offline}). Am Beamer 'Debugging zulassen' bestätigen und erneut starten."
  SERIAL_TAG="${SERIAL//[:.]/_}"
  ok "Verbunden mit $(sh_tv getprop ro.product.model) ($SERIAL)"
}

# Shell-Befehl auf dem Beamer, ohne Windows-Zeilenenden.
sh_tv() { adb -s "$SERIAL" shell "$@" | tr -d '\r'; }

home_launcher() {
  sh_tv cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null \
    | tail -n1 | cut -d/ -f1
}

is_protected() {
  local pkg="$1"
  [[ "$pkg" =~ $PROTECTED_REGEX ]] || [[ "$pkg" == "${HOME_LAUNCHER:-}" ]]
}

# Paketliste ohne Kommentare/Leerzeilen.
load_list() {
  local file="$PKG_DIR/$1.txt"
  [[ -f "$file" ]] || die "Profil '$1' nicht gefunden ($file)."
  grep -vE '^[[:space:]]*(#|$)' "$file" | awk '{print $1}'
}

installed_packages() { sh_tv pm list packages | sed 's/^package://' | sort; }
disabled_packages()  { sh_tv pm list packages -d | sed 's/^package://' | sort; }
