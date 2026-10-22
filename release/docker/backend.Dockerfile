FROM --platform=$BUILDPLATFORM maven:3.9.12-eclipse-temurin-21 AS build
ARG NQ_DISTRIBUTION=NQ_SELF_HOSTED
WORKDIR /source
COPY backend/ ./backend/
RUN case "$NQ_DISTRIBUTION" in NQ_SELF_HOSTED) profile="" ;; NQ_PUBLIC_DEMO) profile="-Ppublic-demo" ;; *) exit 64 ;; esac \
    && mvn -B -f backend/pom.xml $profile -pl nq-app -am -DskipTests install \
    && mvn -B -f backend/nq-app/pom.xml $profile -DskipTests package spring-boot:repackage
FROM eclipse-temurin:21-jre-jammy
ARG NQ_DISTRIBUTION=NQ_SELF_HOSTED
LABEL com.nexusquant.distribution=$NQ_DISTRIBUTION
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && useradd --system --uid 10001 nexusquant
WORKDIR /opt/nexusquant
COPY --from=build /source/backend/nq-app/target/nq-app-*.jar app.jar
ENV NQ_DISTRIBUTION=$NQ_DISTRIBUTION
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
