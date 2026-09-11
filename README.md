# Denkpause

Eine kleine Web-App: kluge Ablenkung statt Kurzvideos – Zitate, Allgemeinwissen, Alltagshilfen, Rätsel, Quiz und "Aktuelle Politik" (live von Wikipedia) zum Zwischendurch-Lernen. Läuft komplett im Browser, alle Daten bleiben lokal auf dem Gerät (localStorage), es gibt keinen Server und kein Tracking.

## Mit GitHub Pages veröffentlichen

1. Dieses Repository bei GitHub anlegen (falls noch nicht geschehen) und den kompletten Inhalt dieses Ordners hochladen (`index.html`, `manifest.json`, `icons/`, `.nojekyll`).
2. Im Repository zu **Settings → Pages** gehen.
3. Unter **Build and deployment** die Quelle auf **Deploy from a branch** stellen.
4. Branch **main** (oder den Branch, in dem die Dateien liegen) und Ordner **/ (root)** auswählen, dann **Save**.
5. Nach ein bis zwei Minuten ist die Seite unter `https://<dein-benutzername>.github.io/<repo-name>/` erreichbar.

Die App funktioniert unabhängig davon, ob sie direkt unter der Domain oder in einem Unterordner (z. B. `.../repo-name/`) liegt – alle Pfade sind relativ gehalten.

## In Chrome installieren

Nach dem Öffnen der GitHub-Pages-URL in Chrome (Desktop oder Android) kann die App über **App installieren** (Symbol in der Adressleiste bzw. Menüpunkt) wie eine native App auf den Homescreen bzw. ins Startmenü gelegt werden.

## Ordnerstruktur

```
index.html            - die komplette App (HTML/CSS/JS in einer Datei)
manifest.json          - PWA-Manifest (Name, Icons, Startfarben)
sw.js                   - Service Worker (Voraussetzung fuer "App installieren" in Chrome)
icons/                  - App-Icons in verschiedenen Größen
.nojekyll               - verhindert, dass GitHub Pages die Dateien durch Jekyll verarbeitet
```

## "App installieren" erscheint nicht in Chrome?

Chrome zeigt den Installieren-Button nur, wenn alle Voraussetzungen erfüllt sind:
- gültiges Manifest mit Icons (192px & 512px) ✓
- Seite läuft über **HTTPS** (GitHub Pages liefert das automatisch) ✓
- ein registrierter **Service Worker** mit Fetch-Handler ✓ (Datei `sw.js`)

Falls es trotzdem nicht klappt:
- Prüfen, ob `sw.js` wirklich mit hochgeladen wurde (im Browser `deine-url/sw.js` direkt aufrufen - sollte Code anzeigen, kein 404)
- Einmal die Seite neu laden (der Service Worker braucht beim allerersten Besuch einen zweiten Ladevorgang, um die Kontrolle zu übernehmen)
- Browser-Cache leeren bzw. die Seite mit Umschalt+Neuladen neu laden, falls vorher schon eine ältere Version ohne Service Worker besucht wurde
- In Chrome unter `chrome://serviceworker-internals` oder den DevTools (Anwendung → Service Worker) nachsehen, ob er als "activated and running" angezeigt wird

## Hinweise

- "Aktuelle Politik" und der Wikipedia-Zufallsmodus laden live Inhalte von de.wikipedia.org und brauchen eine Internetverbindung. Alle anderen Kategorien funktionieren offline.
- Wetter im Kalender-Tab nutzt die kostenlose Open-Meteo-API (kein Schlüssel nötig).
- Es gibt kein Backend: alle Fortschritte, Favoriten und Einstellungen liegen ausschließlich im localStorage des jeweiligen Browsers. Über "Exportieren" in den Einstellungen lässt sich ein Backup als JSON-Datei sichern, über "Importieren" wieder einspielen.
