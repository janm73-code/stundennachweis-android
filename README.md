# Eigenständige Android-App

Version 2.0 ist keine Browser-Verknüpfung mehr. Tagesansicht, Kalender,
Spracheingabe, Einträge und PDF-Erzeugung laufen direkt auf dem Gerät. Nur die
optionale fachliche KI-Korrektur benötigt Internet und einen OpenAI-API-Schlüssel.

## Funktionen

- Originalraster für Montag bis Freitag einschließlich Frühstück,
  Mittagessen sowie Pausen-, Früh- und Spätaufsicht
- native deutsche Spracheingabe mit drei Sekunden Nachlauf
- Löschbefehle wie „Lösche alles“ oder „Lösche die erste Stunde“
- fachliche Formulierung mit lokaler Rechtschreibkorrektur und optionaler KI
- farbige Monatsübersicht für fehlende, teilweise und vollständige Tage
- genau zwei PDF-Seiten je Kalenderwoche
- automatische Ablage unter `Dokumente/Stundennachweise/Schuljahr_…`
- Startbildschirm-Widget für die direkte Spracheingabe

## Build

`./gradlew assembleDebug`
