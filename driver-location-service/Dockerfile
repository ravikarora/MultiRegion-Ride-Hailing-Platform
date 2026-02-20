FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY pom.xml .
COPY shared-lib/pom.xml shared-lib/
COPY shared-lib/src shared-lib/src/
COPY driver-location-service/pom.xml driver-location-service/
COPY driver-location-service/src driver-location-service/src/
RUN --mount=type=cache,target=/root/.m2 \
    apk add --no-cache maven && \
    mvn -pl shared-lib,driver-location-service -am package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/driver-location-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
