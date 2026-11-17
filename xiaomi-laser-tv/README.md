# Xiaomi 4K Laser TV optimieren

Entschlacken, schneller machen und Zwischenbildberechnung richtig nutzen.
Ohne Root, alles umkehrbar.

Die ausführliche Anleitung ist `index.html` (statische Seite, z. B. auf Vercel
mit Root Directory `xiaomi-laser-tv` deployen oder lokal im Browser öffnen).

## Schnellstart

```bash
cd scripts
./01-optimize-all.sh 192.168.178.50 --apply   # alles auf einmal (Schritte unten einzeln)
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
| `scripts/01-optimize-all.sh` | **Ein-Klick:** Inventur + Entschlacken + Performance nacheinander, zeigt am Ende die Kino-Einstellungen |
| `scripts/00-inventory.sh` | Inventur, schreibt `backup/inventory-*.txt` |
| `scripts/10-debloat.sh` | deaktiviert Pakete nach Profil, protokolliert in `backup/changes-*.log` |
| `scripts/20-performance.sh` | Settings-Tweaks, sichert Originalwerte in `backup/settings-*.txt` |
| `scripts/40-install-kino-licht.sh` | installiert die Kino-Licht-App (LED-Steuerung für Hyperion) |
| `scripts/50-capture-probe.sh` | prüft, ob der Beamer das laufende (HDMI-)Bild an Aufnahmen herausgibt |
| `scripts/60-root-check.sh` | prüft gefahrlos, ob sich der Beamer rooten lässt (ändert nichts) |
| `scripts/99-restore.sh` | macht beides rückgängig, auch einzeln pro Paket |
| `packages/*.txt` | Profile `safe`, `xiaomi`, `optional` |
| `kino-licht/` | Android-TV-App zur Steuerung von Hyperion.ng ([README](kino-licht/README.md)) |

## Beste Kino-Parameter

Fertige Einstellwerte für eine kinoähnliche Wiedergabe (Apple TV + Beamer):
siehe [CINEMA.md](CINEMA.md). `01-optimize-all.sh` zeigt sie am Ende ebenfalls an.

## Grenzen

- **Zwischenbildberechnung nachrüsten:** MEMC macht der Chip im Beamer. Die
  Skripte und die Anleitung sorgen für das passende Eingangssignal (Apple TV
  „Bildrate anpassen“) und die richtige MEMC-Stufe.

## Nächster Schritt

`00-inventory.sh` laufen lassen und die erzeugte Datei teilen – dann wird
`packages/xiaomi.txt` auf dein exaktes Modell zugeschnitten.
