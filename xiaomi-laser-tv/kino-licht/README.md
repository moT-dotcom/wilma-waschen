# Kino-Licht

Android-TV-App für den Xiaomi Laser TV, die deine LEDs über **Hyperion.ng** auf
dem Raspberry Pi steuert. Komplett mit der Fernbedienung bedienbar.

## Funktionen

| Bereich | Was geht |
| --- | --- |
| **Jetzt** | LEDs an/aus, Helligkeit, Szenen per Klick, Einschlaf-Timer (15–120 min, blendet sanft aus), „Zurück zu Hyperion“ |
| **Farben** | 24er-Palette, Weißtöne von Kerze bis Tageslicht (1500–6500 K), Feinwahl nach Farbton/Sättigung/Leuchtkraft, Favoriten. Jede Änderung leuchtet sofort |
| **Farbverläufe** | Mehrfarbige Szenen rund um die Leinwand (Rundherum, Links→Rechts, Oben→Unten, Gespiegelt), 8 Vorlagen wie Sonnenuntergang oder Nordlicht |
| **Effekte** | Alle Effekte deines Hyperion mit deutschen Namen, Favoriten oben |
| **Szenen** | Eigene Szenen aus Farbe, Verlauf, Effekt, „Hyperion-Standard“ oder „Aus“, jeweils mit eigener Helligkeit, Symbol und Reihenfolge |
| **Zeitplan** | Nach Uhrzeit oder Sonnenauf-/-untergang (± Versatz), Wochentage, Aktionen: Szene, an, aus, ausblenden, **Lichtwecker** (simulierter Sonnenaufgang), Helligkeit über Zeit ändern. Optional „nur wenn der Beamer läuft“. Vorlagen per Klick |
| **Automatik** | LEDs gehen mit dem Beamer an und aus. Beim Einschalten tagsüber und abends verschiedene Szenen. Abend nach Sonnenuntergang oder fester Uhrzeit |
| **Verbindung** | Hyperion automatisch im Netzwerk finden, Token per Knopfdruck anfordern, Instanz und Priorität wählen, Standort für Sonnenzeiten (offline berechnet) |

## Installation

Die APK baut GitHub Actions bei jedem Push und veröffentlicht sie als Release
`kino-licht`. Installieren vom Rechner aus:

```bash
cd xiaomi-laser-tv/scripts
./40-install-kino-licht.sh 192.168.178.50
```

Das Skript nimmt die App außerdem von der Energiesparsteuerung aus, damit
Zeitpläne auch im Standby laufen. Ist das Repo privat, die APK von der
Release-Seite laden und als zweiten Parameter übergeben.

Danach am Beamer: **Verbindung → Im Netzwerk suchen** → Hyperion auswählen. Fertig.

## Technik

- `core/`: Kernlogik ohne Android-Abhängigkeiten: TCP-JSON-Client für
  Hyperion.ng (Port 19444, wie Home Assistant), Szenen, Zeitpläne,
  Sonnenstandsberechnung, Farbverläufe. Mit Unit-Tests gegen einen simulierten
  Hyperion-Server: `./gradlew -p core test`
- `app/`: Jetpack Compose für Android TV (ab Android 7), Vordergrunddienst für
  die Beamer-Kopplung, AlarmManager für Zeitpläne, mDNS-Suche
  (`_hyperiond-json._tcp`)
- Farbverläufe werden als 64×36-Bild an Hyperion geschickt; Hyperion verteilt die
  Bildränder auf die LEDs
- Helligkeitsübergänge nutzen Hyperions `adjustment`, der Lichtwecker setzt
  schrittweise Farben von 1000 K bis 5500 K
- Signiert mit einem festen Schlüssel im Repo (`keystore/`), damit Updates ohne
  Deinstallation funktionieren. Für den privaten Gebrauch gedacht, nicht für
  den Play Store

## Grenzen

- Ob am Apple TV gerade ein Film läuft oder pausiert ist, sieht der Beamer
  nicht, deshalb gibt es kein automatisches „Pause = Licht an“.
- Geht der Beamer in einen tiefen Standby und trennt dabei das WLAN, können
  Zeitpläne zu dieser Zeit Hyperion nicht erreichen. Die Ausnahme von der
  Energiesparsteuerung (Installationsskript) hilft, garantiert ist es je nach
  Firmware nicht.
