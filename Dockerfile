FROM node:22-alpine AS frontend
WORKDIR /ui
COPY frontend/package*.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-17 AS backend
WORKDIR /build
COPY pom.xml ./
COPY src/ ./src/
COPY --from=frontend /ui/dist/ ./src/main/resources/static/
RUN mvn -B verify

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app && mkdir /app/data && chown app:app /app/data
COPY --from=backend /build/target/transaction-risk-review-0.2.0.jar /app/app.jar
USER app
ENV JAVA_TOOL_OPTIONS="-Xms64m -Xmx256m"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
