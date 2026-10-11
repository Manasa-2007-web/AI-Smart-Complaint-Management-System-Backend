
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY . .
RUN chmod +x mvnw && ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre

RUN apt-get update && apt-get install -y --no-install-recommends \
    python3 python3-pip \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar
COPY ai /app/ai
COPY models /app/models

RUN pip3 install --no-cache-dir --break-system-packages \
    -r /app/ai/requirements.txt

EXPOSE 8080

CMD ["java", "-jar", "/app/app.jar"]
