# ThirdSpace

**Privates, persönliches Entwicklungsprojekt.**

ThirdSpace ist eine App zum Entdecken, Sammeln und Teilen von Orten. Das Projekt verbindet eine native iOS-Oberfläche in SwiftUI mit einer geteilten Daten- und Geschäftslogik in Kotlin Multiplatform.

## Funktionen und Entwicklungsstand

Im aktuellen Code umgesetzt:

- Mapbox-Karte mit Ortsmarkierungen, Detailansichten und dem Anlegen eigener Orte; Mapbox-POIs können als Ausgangspunkt übernommen werden.
- Feed mit ortsbezogenen Beiträgen, Kommentaren und Bewertungen.
- Registrierung und Anmeldung über Supabase sowie Profilbearbeitung und Folgen anderer Nutzer.
- Eigene Sammlungen zum Speichern von Orten.
- Lokaler SQLDelight-Cache für Orte und Feed-Daten sowie Warteschlangen für ausgewählte Schreibvorgänge.
- Gemeinsame UI-Texte für Deutsch und Englisch.

Das Projekt befindet sich in Entwicklung. Die iOS-App enthält die beschriebenen Oberflächen; `androidApp/` ist derzeit ein Startplatzhalter. Der Discover-Tab, die Kartensuche und einzelne Kartenbedienelemente sind ebenfalls Platzhalter. Backend-Funktionen benötigen eine konfigurierte Supabase-Instanz und eine Anmeldung. Für Folgen und Sammlungen gibt es keine dauerhafte Offline-Warteschlange.

## Technik und Struktur

**SwiftUI · Kotlin Multiplatform · Coroutines · Ktor · Supabase/PostgreSQL · SQLDelight · Mapbox**

| Pfad | Inhalt |
| --- | --- |
| `iosApp/iosApp/` | Native Views, Navigation und UI-Zustand |
| `shared/src/commonMain/` | Modelle, Repositories, Cache, SQL-Schema und Übersetzungen |
| `shared/src/iosMain/` | Swift-Anbindung über `FeedBridge` und iOS-spezifische Implementierungen |
| `shared/src/commonTest/`, `shared/src/androidHostTest/` | Tests für Datenlogik, Caching und Datenbankmigrationen |
| `supabase/migrations/` | PostgreSQL-Schema, Views und Zugriffsregeln |
| `androidApp/` | Android-Einstiegspunkt mit Platzhalteroberfläche |

Die SwiftUI-Oberfläche greift über eine Bridge mit einfachen Datenwerten auf die Kotlin-Repositories zu. Netzwerk- und Datenbankzugriffe bleiben im geteilten Modul.

## Lokal einrichten

1. Das Xcode-Projekt `iosApp/iosApp.xcodeproj` öffnen; für das Shared-Modul wird die Kotlin-/Gradle-Toolchain benötigt.
2. `iosApp/Mapbox.xcconfig.template` und `iosApp/Supabase.xcconfig.template` jeweils ohne `.template` kopieren und eigene Werte eintragen. Die Supabase-URL wird dort als Host ohne `https://` hinterlegt.
3. Für den Mapbox-SDK-Download die Hinweise in [netrc.template](iosApp/netrc.template) beachten.
4. Die SQL-Dateien in `supabase/migrations/` in Dateinamenreihenfolge auf eine eigene Supabase-Instanz anwenden und die iOS-App in Xcode starten.

Die lokalen Konfigurationsdateien sind von Git ausgeschlossen. Ohne Backend-Konfiguration sind die Anmelde- und Serverfunktionen nicht verfügbar.
