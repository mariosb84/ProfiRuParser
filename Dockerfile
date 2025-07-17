# Используем официальный образ Java 17
FROM eclipse-temurin:17-jdk-jammy

# Установка Chrome
RUN apt-get update && apt-get install -y wget && \
    wget -q https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb && \
    apt-get install -y ./google-chrome-stable_current_amd64.deb && \
    rm google-chrome-stable_current_amd64.deb

# 1. Устанавливаем рабочую директорию
WORKDIR /app

# 2. Копируем JAR-файл в контейнер
COPY target/*.jar app.jar

# Флаг для детекта контейнера
ENV INSIDE_DOCKER=true

# 3. Открываем порт приложения
EXPOSE 8080

# 4. Команда запуска
ENTRYPOINT ["java", "-jar", "app.jar"]