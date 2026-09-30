FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY services ./services
RUN mvn -q -Dmaven.test.skip=true package
ARG SERVICE

FROM eclipse-temurin:25-jre
WORKDIR /app
ARG SERVICE
COPY --from=build /workspace/services/${SERVICE}/target/${SERVICE}-1.0.0-SNAPSHOT.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-jar", "/app/app.jar"]
