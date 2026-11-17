# Beste Parameter für kinoähnliche Wiedergabe

Kurzfassung für den Xiaomi 4K Laser TV mit Apple TV als Zuspieler. Reihenfolge
ist Absicht: Erst das Signal am Apple TV stimmig machen, dann das Bild am
Beamer. Im abgedunkelten Raum schauen – Laserbeamer leben vom Kontrast.

## 1. Apple TV — Einstellungen → Video und Audio

| Einstellung | Wert | Warum |
| --- | --- | --- |
| Format | **4K SDR 60 Hz** | Menü in SDR, Filme schalten selbst auf HDR |
| Inhalt anpassen → Dynamikbereich | **Ein** | HDR nur, wenn der Film HDR ist |
| Inhalt anpassen → **Bildrate** | **Ein** | **Der wichtigste Schalter.** Filme kommen als echte 24p, nicht als 60 Hz mit 3:2-Ruckeln |
| Chroma | **4:2:2** | volle 4K60-HDR-Farbe innerhalb der HDMI-2.0-Bandbreite |
| HDMI-Verbindung prüfen | einmal ausführen | bestätigt, dass 4K/HDR/24p ankommen |

> Ohne „Bildrate anpassen“ wird 24p-Material in 60 Hz gepresst (Bilder abwechselnd
> 3×/2× gezeigt). Das Ruckeln bekommt auch die beste Bewegungsglättung nicht sauber weg.

## 2. Beamer — Bild → (Erweitert)

Menünamen je nach Firmware leicht anders; der nächstliegende Punkt passt.

| Einstellung | Wert | Warum |
| --- | --- | --- |
| Bildmodus | **Film / Kino / Movie** | kalibriert am nächsten an der Kino-Norm |
| Farbtemperatur | **Warm / Warm2** | Richtung 6500 K (D65); „Standard/Kühl“ ist zu blau |
| Gamma | **2.2** (dunkler Raum: **2.4**) | Durchzeichnung in dunklen Szenen |
| Helligkeit (Schwarzwert) | so, dass Schwarz satt bleibt, Schatten aber sichtbar | kein „Schwarz säuft ab“ |
| Kontrast | hoch, aber ohne Clipping der hellsten Details | Spitzlichter bleiben gezeichnet |
| Schärfe | **0 / neutral** | höhere Werte erzeugen künstliche Kantenringe |
| Rauschunterdrückung (NR/MPEG-NR) | **Aus** | frisst Filmkorn und feine Details |
| Dynamischer Kontrast | **Aus** | pumpt die Helligkeit; stört den Kino-Look |
| Bewegung / MEMC | **Niedrig** für Filme · **Aus** für Spiele | etwas flüssiger ohne „Seifenoper-Effekt“ |
| Local Dimming / Laser-Leistung | HDR: hoch · dunkler Raum SDR: mittel | Kontrast gegen Umgebungslicht |
| HDMI-Modus des Apple-TV-Ports | **Erweitert / 2.0** | sonst kein 4K60 HDR |

### MEMC (Zwischenbildberechnung) je Inhalt

| Inhalt | Bildmodus | MEMC |
| --- | --- | --- |
| Kinofilm | Film/Kino | **Niedrig** (oder Aus für puren 24p-Look) |
| Serie, Doku | Film/Standard | Niedrig–Mittel |
| Sport, Live | Sport/Standard | Hoch |
| Spiele | Spiel | **Aus** (Spielmodus senkt die Latenz) |

## 3. Ton (falls über den Beamer)

- Dynamikkompression / Nachtmodus **aus** für vollen Kinoklang (abends ggf. an).
- Läuft der Ton über AVR/Soundbar: am Apple TV Audioformat auf **Automatisch**,
  damit Dolby/DTS durchgereicht wird; die interne Beamer-Lautstärke dann fix.

## 4. Was die Skripte schon erledigen

`01-optimize-all.sh … --apply` entschlackt den Beamer, kürzt Animationen,
schaltet HDMI-CEC ein (Apple TV weckt/schaltet den Beamer) und optimiert die
Apps neu. Die Bildparameter oben liegen im Bildmenü und werden bewusst nicht
automatisch gesetzt — sie sind Geschmack und hängen von Raum und Leinwand ab.

Alles umkehrbar: `./99-restore.sh <IP>`.
