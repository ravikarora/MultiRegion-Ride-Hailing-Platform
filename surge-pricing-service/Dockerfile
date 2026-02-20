FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY pom.xml .
COPY shared-lib/pom.xml shared-lib/
COPY shared-lib/src shared-lib/src/
COPY surge-pricing-service/pom.xml surge-pricing-service/
COPY surge-pricing-service/src surge-pricing-service/src/
RUN --mount=type=cache,target=/root/.m2 \
    apk add --no-cache maven && \
    mvn -pl shared-lib,surge-pricing-service -am package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/surge-pricing-service/target/*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
