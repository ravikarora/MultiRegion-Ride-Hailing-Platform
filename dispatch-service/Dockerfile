FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY pom.xml .
COPY shared-lib/pom.xml shared-lib/
COPY shared-lib/src shared-lib/src/
COPY dispatch-service/pom.xml dispatch-service/
COPY dispatch-service/src dispatch-service/src/
RUN --mount=type=cache,target=/root/.m2 \
    apk add --no-cache maven && \
    mvn -pl shared-lib,dispatch-service -am package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/dispatch-service/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
