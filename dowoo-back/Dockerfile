FROM node:22-alpine AS frontend-build
WORKDIR /frontend
COPY dowoo/package.json dowoo/package-lock.json ./
RUN npm ci
COPY dowoo/ ./
RUN npm run build

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY dowoo-back/build.gradle dowoo-back/settings.gradle ./
COPY dowoo-back/gradle ./gradle
COPY dowoo-back/gradlew ./
RUN chmod +x gradlew && ./gradlew --version
COPY dowoo-back/src ./src
COPY --from=frontend-build /frontend/dist ./src/main/resources/static
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
