# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests -Dspotless.check.skip=true package

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 slotwise
WORKDIR /app
COPY --from=build /workspace/target/slotwise-*.jar app.jar
USER slotwise
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
