# Scan Service Dockerfile
FROM openjdk:17-jdk-slim AS build
WORKDIR /app

# Copy Maven files
COPY pom.xml .
COPY src ./src

# Build the application
RUN ./mvnw clean package -DskipTests

# Runtime stage
FROM openjdk:17-jdk-slim
WORKDIR /app

# Copy the built JAR
COPY --from=build /app/target/*.jar app.jar

# Expose port 8083 (Scan service port)
EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]