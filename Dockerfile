FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Создаем пользователя для безопасности (чтобы не запускать от root)
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Копируем уже собранный JAR (важно: он должен быть собран на хосте)
COPY build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]