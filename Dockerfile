# Stage 1: Builder/Extractor
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /builder

# The JAR file is built by Maven in the CI/CD pipeline before Docker build
# We copy it into the builder image
ARG JAR_FILE=nanogate-app/target/nanogate-app-*.jar
COPY ${JAR_FILE} application.jar

# Extract the Spring Boot layered JAR using the tools mode
RUN java -Djarmode=tools -jar application.jar extract --layers --launcher --destination extracted

# Stage 2: Runtime
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Create a non-root user for enhanced security
RUN addgroup -S nanogate && adduser -S nanogate -G nanogate
USER nanogate:nanogate

# Copy the extracted layers in order of change frequency
# Dependencies change least frequently, Application code changes most frequently
COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./

# Expose the default API Gateway port
EXPOSE 8080

# Expose the management port if configured differently (optional)
EXPOSE 8081

# Launch the application using the JarLauncher which optimizes class loading
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
