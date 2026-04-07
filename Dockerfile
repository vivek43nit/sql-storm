# syntax=docker/dockerfile:1
# Multi-arch build: docker buildx build --platform linux/amd64,linux/arm64 .

# ── Stage 1: build frontend ─────────────────────────────────────────────────
FROM --platform=$BUILDPLATFORM node:20-alpine AS frontend-build
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# ── Stage 2: build backend + embed frontend ──────────────────────────────────
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-17 AS backend-build
WORKDIR /app/backend
COPY backend/pom.xml ./
RUN mvn dependency:go-offline -q
COPY backend/src ./src
COPY --from=frontend-build /app/frontend/dist ./src/main/resources/static
RUN mvn package -DskipTests -q

# ── Stage 3: runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
ARG TARGETARCH
WORKDIR /app

COPY --from=backend-build /app/backend/target/*.jar app.jar

# Default config location — mount your DatabaseConnection.xml here
VOLUME /etc/fkblitz

EXPOSE 9044

ENTRYPOINT ["java", "-jar", "app.jar"]
