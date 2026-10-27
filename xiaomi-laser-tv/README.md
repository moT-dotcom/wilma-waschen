# Xiaomi 4K Laser TV optimieren

Entschlacken, schneller machen, Zwischenbildberechnung richtig nutzen und das
Apple-TV-Bild für Hyperion/HyperHDR auf dem Raspberry Pi abgreifen.
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

Am Raspberry Pi:

```bash
./hyperion/pi-capture-check.sh              # USB-Grabber, Signal, HDCP-Hinweis, Dienste
```

## Aufbau

| Pfad | Inhalt |
| --- | --- |
| `scripts/lib.sh` | ADB-Verbindung, Schutzliste (System, HDMI, Launcher …) |
| `scripts/00-inventory.sh` | Inventur, schreibt `backup/inventory-*.txt` |
| `scripts/10-debloat.sh` | deaktiviert Pakete nach Profil, protokolliert in `backup/changes-*.log` |
| `scripts/20-performance.sh` | Settings-Tweaks, sichert Originalwerte in `backup/settings-*.txt` |
| `scripts/99-restore.sh` | macht beides rückgängig, auch einzeln pro Paket |
| `packages/*.txt` | Profile `safe`, `xiaomi`, `optional` |
| `hyperion/pi-capture-check.sh` | Prüfskript für den Pi |

## Was bewusst nicht geht

- **Zwischenbildberechnung nachrüsten:** MEMC macht der Chip im Beamer. Die
  Skripte und die Anleitung sorgen für das passende Eingangssignal (Apple TV
  „Bildrate anpassen“) und die richtige MEMC-Stufe.
- **Hyperion-Capture im Beamer:** HDMI-Eingänge laufen über einen
  Hardware-Videopfad und sind HDCP-geschützt; Android-Aufnahme-Apps sehen
  nur Schwarz. Lösung: HDMI-Splitter vor dem Beamer + USB-Grabber am Pi.

## Nächster Schritt

`00-inventory.sh` laufen lassen und die erzeugte Datei teilen – dann wird
`packages/xiaomi.txt` auf dein exaktes Modell zugeschnitten.
