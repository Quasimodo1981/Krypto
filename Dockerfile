# --- Phase 1: Build-Umgebung ---
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

# Kopiere die pom.xml-Dateien und den Source-Code aller Module
COPY pom.xml .
COPY common ./common
COPY server ./server

# Baut das Server-Modul inklusive aller Abhängigkeiten (wie common)
RUN mvn clean package -pl server -am -DskipTests

# --- Phase 2: Leichtgewichtiges Laufzeit-Image ---
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Kopiere die gebaute JAR-Datei und die generierten Dependencies aus Phase 1
COPY --from=builder /app/server/target/server-1.0-SNAPSHOT.jar ./server.jar
COPY --from=builder /app/server/target/dependency ./dependency

# Exponiere den dynamischen Port für Render
EXPOSE 10000

# Startbefehl für den KryptoCentralServer
CMD ["java", "-cp", "server.jar:dependency/*", "server.KryptoServer"]