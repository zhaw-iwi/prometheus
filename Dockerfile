FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

RUN apk add --no-cache bash

COPY . .

RUN sed -i 's/\r$//' mvnw
RUN chmod +x mvnw
RUN ./mvnw --batch-mode --no-transfer-progress clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
RUN addgroup -S prometheus && adduser -S prometheus -G prometheus
COPY --from=build --chown=prometheus:prometheus /workspace/target/prometheus-0.0.1-SNAPSHOT.jar /app/prometheus.jar

USER prometheus
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-jar", "/app/prometheus.jar"]
