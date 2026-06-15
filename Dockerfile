FROM eclipse-temurin:21.0.1_12-jdk-alpine

WORKDIR /prometheus

COPY . .

# Cleanup file
RUN sed -i 's/\r$//' mvnw
RUN chmod +x mvnw

# Build JAR
RUN ./mvnw clean install -DskipTests

# Run application with production profile
CMD ./mvnw spring-boot:run -Dspring-boot.run.profiles=prod