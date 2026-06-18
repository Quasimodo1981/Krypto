# --- Phase 1: Build-Umgebung ---
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

# Kopiere die pom.xml-Dateien und den Source-Code ALLER Module
COPY pom.xml .
COPY common ./common
COPY server ./server
COPY client ./client

# Baut das Server-Modul inklusive aller Abhängigkeiten (wie common)
RUN mvn clean package -pl server -am -DskipTests

# --- Phase 2: Leichtgewichtiges Laufzeit-Image ---
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Kopiere die gebaute JAR-Datei und die generierten Dependencies aus Phase 1
# WICHTIG: Prüfe hier kurz den JAR-Namen. Da Maven "server-1.0-SNAPSHOT.jar" generiert,
# passen wir das hier exakt an den Namen aus deiner Fehlermeldung an.
COPY --from=builder /app/server/target/server-1.0-SNAPSHOT.jar ./server.jar
COPY --from=builder /app/server/target/dependency ./dependency

# Exponiere den dynamischen Port für Render
EXPOSE 10000

# Startbefehl für den KryptoCentralServer
CMD ["java", "-cp", "server.jar:dependency/*", "server.KryptoServer"]