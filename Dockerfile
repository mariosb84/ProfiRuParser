# Используем официальный образ Java 17
FROM eclipse-temurin:17-jdk-jammy

# Установка Chrome и зависимостей
RUN apt-get update && apt-get install -y wget gnupg && \
    wget -q -O - https://dl.google.com/linux/linux_signing_key.pub | apt-key add - && \
    echo "deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main" >> /etc/apt/sources.list.d/google-chrome.list && \
    apt-get update && apt-get install -y google-chrome-stable && \
    rm -rf /var/lib/apt/lists/*

# Устанавливаем рабочую директорию
WORKDIR /app

# Копируем JAR-файл в контейнер
COPY target/*.jar app.jar

# Открываем порт приложения
EXPOSE 9900

# Команда запуска с профилем Docker
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=docker"]