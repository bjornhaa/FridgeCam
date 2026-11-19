# syntax=docker/dockerfile:1.7
# To trinn, som i talik-ai: bygg jar med frontend, kjør på en ren JRE.

# --- Bygg ---------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-25 AS bygg
WORKDIR /workspace

# Node (lastet ned av frontend-maven-plugin) trenger libatomic, som imaget mangler.
RUN apt-get update && apt-get install -y --no-install-recommends libatomic1 && rm -rf /var/lib/apt/lists/*

# Avhengighetene først, så de caches så lenge pom.xml er uendret.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
COPY frontend ./frontend
# Testene kjøres i CI før deploy; her bygges bare jar-en (frontend-maven-plugin
# henter Node selv, så imaget trenger ikke Node).
RUN mvn -B -q -Pwith-frontend -DskipTests package

# --- Kjøring ------------------------------------------------------------------
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 10001 fridgecam
COPY --from=bygg /workspace/target/fridgecam-*.jar /app/app.jar
USER fridgecam

# JVM-en leser JAVA_TOOL_OPTIONS selv; heap som andel av maskinens minne.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
