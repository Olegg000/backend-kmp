# --- Stage 1: Сборка (Builder) ---
FROM gradle:8.5-jdk21-alpine AS builder
WORKDIR /app

# Копируем файлы Gradle (для кэширования зависимостей)
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle

# Копируем исходный код
COPY src ./src

# Собираем приложение (пропускаем тесты для скорости сборки в контейнере)
RUN gradle bootJar --no-daemon -x test

# --- Stage 2: Запуск (Runtime) ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Создаем пользователя для безопасности (чтобы не запускать от root)
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Копируем собранный JAR из первого этапа
COPY --from=builder /app/build/libs/*.jar app.jar

# Порт, на котором работает Spring (по умолчанию 8080)
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]