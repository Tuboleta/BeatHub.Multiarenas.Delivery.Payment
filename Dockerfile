# =============================================================================
# Stage 1: Build & Package (Java 25 JDK Alpine + Maven Wrapper)
# =============================================================================
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /app

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN ./mvnw dependency:go-offline -B

COPY src ./src
RUN ./mvnw clean package -Dmaven.test.skip=true

# =============================================================================
# Stage 2: Runtime Image (Eclipse Temurin 25 JRE Alpine)
# =============================================================================
FROM eclipse-temurin:25-jre-alpine AS runner
WORKDIR /app

RUN addgroup -S beathub && adduser -S beathub -G beathub

COPY --from=builder /app/target/*.jar app.jar

RUN chown -R beathub:beathub /app
USER beathub

ENV SERVER_PORT=8083 \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8083

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
