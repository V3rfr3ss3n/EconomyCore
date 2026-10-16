# TNE auf Paper 26.3 aktualisieren

Dieses lokale Paket basiert auf BitworksMC/EconomyCore 0.1.5.3. Es enthält außerdem die Fixes aus dem bisherigen V3rfr3ss3n-Fork für stabile Währungs-UUIDs beim Laden und das sofortige Anstoßen der Kontostand-Speicherung. Die Paper-API wurde auf 26.3.build.178-stable und Adventure auf 5.2.0 aktualisiert. Eine in Paper 26.3 entfernte Map-Color-Komponente aus der mitgelieferten TNIL-Bibliothek wird beim Laden deaktiviert.

## Austausch

1. Lobby und Vanilla sauber stoppen. Die bereits erstellten Backups aufbewahren.
2. Auf **beiden** Paper-Servern die bisherige `TNE-Paper-0.1.4.2.jar` aus `plugins/` entfernen und `TNE-Paper-0.1.5.3-custom.jar` hochladen.
3. Die vorhandenen TNE-Konfigurationen und die MariaDB-Datenbank unverändert lassen. TNE aktualisiert `data.yml` beim Start auf Konfigurationsversion 7.
4. Velocity braucht kein TNE-JAR. Der Community-Fork hat die Velocity-Bridge entfernt. Die bisherige Velocity-Plugin-Liste aus deinem Screenshot enthält bereits kein TNE.
5. Erst Lobby, dann Vanilla starten. In beiden Logs prüfen, ob `TheNewEconomy v0.1.5.3` erfolgreich aktiviert wird. Danach einen kleinen Kontostands-/Zahlungstest durchführen.

## Kontostände zwischen Lobby und Vanilla

Ab 0.1.4.3 benutzt TNE für aktive Synchronisierung zwischen Servern Redis. In 0.1.5.3 ist `Data.Sync.Enabled` standardmäßig `false`. Wenn derselbe Kontostand auf beiden Servern sofort sichtbar sein muss, auf beiden Backends denselben erreichbaren Redis-Server und denselben langen `Data.Sync.Security.Token` in `plugins/TheNewEconomy/data.yml` eintragen, `Data.Sync.Enabled: true` setzen und beide Server vollständig neu starten. Die gemeinsame MariaDB ersetzt diese Echtzeit-Synchronisierung nicht.

Im bisherigen Lobby-Startlog hat Vault **Essentials Economy** als Anbieter gewählt; auf Vanilla hat VPlugin **TheNewEconomy** gewählt. Wenn dieselbe Wirtschaft auf beiden Servern gelten soll, den Vault-Anbieter auf der Lobby vor dem produktiven Einsatz prüfen.

Die JAR wurde per `mvn -B -pl PaperCore -am package` gebaut. Dabei wurden keine Tests gefunden. Ein isolierter Start und sauberer Stopp des endgültigen Builds mit Paper 26.3 Build 194 und Java 27 waren erfolgreich. Für den Build wird JDK 25 oder neuer benötigt. Der Test benutzte eine frische YAML-Konfiguration; die Kombination mit deiner MariaDB, Vault und deinen übrigen Plugins ist noch nicht geprüft. Der Update-Checker meldete im Test weiterhin `Unable to contact update server!`, während TNE vollständig aktiviert war. Ein zusätzlicher `mvn verify`-Lauf scheiterte im vorhandenen PMD 7.17.0 unter JDK 27 mit einer internen Typauflösungs-Exception. Falls du Map-Items als Währung verwendest, teste deren Erkennung vor dem produktiven Einsatz.
