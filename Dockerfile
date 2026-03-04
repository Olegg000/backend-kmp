FROM eclipse-temurin:21-jre-alpine

# Создаем пользователя для безопасности (чтобы не запускать от root)
RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

# Готовим директорию логов и выдаем права пользователю приложения
RUN mkdir -p /app/logs && chown -R spring:spring /app

# Копируем уже собранный JAR (важно: он должен быть собран на хосте)
COPY --chown=spring:spring build/libs/*.jar app.jar

USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
