# --- Phase 1: Build-Umgebung ---
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app

# Kopiere die pom.xml-Dateien und den Source-Code ALLER Module
COPY pom.xml .
COPY common ./common
COPY server ./server
COPY client ./client

# Baut das Server-Modul und kopiert die Abhängigkeiten direkt in den target-Ordner
RUN mvn clean package dependency:copy-dependencies -DoutputDirectory=server/target/dependency -pl server -am -DskipTests

# --- Phase 2: Leichtgewichtiges Laufzeit-Image ---
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Kopiere die gebaute JAR-Datei (jetzt mit dem korrekten Namen!) und die Dependencies
COPY --from=builder /app/server/target/krypto-server-1.0-SNAPSHOT.jar ./server.jar
COPY --from=builder /app/server/target/dependency ./dependency

# Exponiere den dynamischen Port für Render
EXPOSE 10000

# Startbefehl für den KryptoCentralServer
CMD ["java", "-cp", "server.jar:dependency/*", "server.KryptoServer"]