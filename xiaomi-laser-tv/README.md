# Xiaomi 4K Laser TV optimieren

Entschlacken, schneller machen und Zwischenbildberechnung richtig nutzen.
Ohne Root, alles umkehrbar.

Die ausführliche Anleitung ist `index.html` (statische Seite, z. B. auf Vercel
mit Root Directory `xiaomi-laser-tv` deployen oder lokal im Browser öffnen).

## Schnellstart

```bash
cd scripts
./00-inventory.sh   192.168.178.50          # nur lesen: Modell, Pakete, MEMC-Hinweise
./10-debloat.sh     192.168.178.50          # Probelauf
./10-debloat.sh     192.168.178.50 --apply  # ausführen (Profile safe + xiaomi)
./20-performance.sh 192.168.178.50          # Animationen, Scans, CEC, dexopt
./99-restore.sh     192.168.178.50          # alles zurück
```

## Aufbau

| Pfad | Inhalt |
| --- | --- |
| `scripts/lib.sh` | ADB-Verbindung, Schutzliste (System, HDMI, Launcher …) |
| `scripts/00-inventory.sh` | Inventur, schreibt `backup/inventory-*.txt` |
| `scripts/10-debloat.sh` | deaktiviert Pakete nach Profil, protokolliert in `backup/changes-*.log` |
| `scripts/20-performance.sh` | Settings-Tweaks, sichert Originalwerte in `backup/settings-*.txt` |
| `scripts/40-install-kino-licht.sh` | installiert die Kino-Licht-App (LED-Steuerung für Hyperion) |
| `scripts/99-restore.sh` | macht beides rückgängig, auch einzeln pro Paket |
| `packages/*.txt` | Profile `safe`, `xiaomi`, `optional` |
| `kino-licht/` | Android-TV-App zur Steuerung von Hyperion.ng ([README](kino-licht/README.md)) |

## Grenzen

- **Zwischenbildberechnung nachrüsten:** MEMC macht der Chip im Beamer. Die
  Skripte und die Anleitung sorgen für das passende Eingangssignal (Apple TV
  „Bildrate anpassen“) und die richtige MEMC-Stufe.

## Nächster Schritt

`00-inventory.sh` laufen lassen und die erzeugte Datei teilen – dann wird
`packages/xiaomi.txt` auf dein exaktes Modell zugeschnitten.
