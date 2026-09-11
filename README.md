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
index.html          - die komplette App (HTML/CSS/JS in einer Datei)
manifest.json        - PWA-Manifest (Name, Icons, Startfarben)
icons/                - App-Icons in verschiedenen Größen
.nojekyll             - verhindert, dass GitHub Pages die Dateien durch Jekyll verarbeitet
```

## Hinweise

- "Aktuelle Politik" und der Wikipedia-Zufallsmodus laden live Inhalte von de.wikipedia.org und brauchen eine Internetverbindung. Alle anderen Kategorien funktionieren offline.
- Wetter im Kalender-Tab nutzt die kostenlose Open-Meteo-API (kein Schlüssel nötig).
- Es gibt kein Backend: alle Fortschritte, Favoriten und Einstellungen liegen ausschließlich im localStorage des jeweiligen Browsers. Über "Exportieren" in den Einstellungen lässt sich ein Backup als JSON-Datei sichern, über "Importieren" wieder einspielen.
