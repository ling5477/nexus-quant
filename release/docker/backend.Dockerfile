FROM --platform=$BUILDPLATFORM maven:3.9.12-eclipse-temurin-21 AS build
WORKDIR /source
COPY backend/ ./backend/
RUN mvn -B -f backend/pom.xml -pl nq-app -am -DskipTests install && mvn -B -f backend/nq-app/pom.xml -DskipTests package spring-boot:repackage
FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && useradd --system --uid 10001 nexusquant
WORKDIR /opt/nexusquant
COPY --from=build /source/backend/nq-app/target/nq-app-*.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
