FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY pom.xml .
COPY shared-lib/pom.xml shared-lib/
COPY shared-lib/src shared-lib/src/
COPY notification-service/pom.xml notification-service/
COPY notification-service/src notification-service/src/
RUN --mount=type=cache,target=/root/.m2 \
    apk add --no-cache maven && \
    mvn -pl shared-lib,notification-service -am package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/notification-service/target/*.jar app.jar
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "app.jar"]
