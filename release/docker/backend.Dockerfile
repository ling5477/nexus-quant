FROM --platform=$BUILDPLATFORM maven:3.9.16-eclipse-temurin-21@sha256:99e61abcff91a9b1333463bd8451fb18495d6eba9250ac66a338b518f8278320 AS build
COPY release/docker/security/ /tmp/java-certificate-policy/
# Maven 下载前应用同一份供应商证书阻断策略，避免仅修复最终运行层。
RUN sh /tmp/java-certificate-policy/install-certificate-policy.sh
ARG NQ_DISTRIBUTION=NQ_SELF_HOSTED
WORKDIR /source
COPY backend/ ./backend/
RUN case "$NQ_DISTRIBUTION" in NQ_SELF_HOSTED) profile="" ;; NQ_PUBLIC_DEMO) profile="-Ppublic-demo" ;; *) exit 64 ;; esac \
    && mvn -B -f backend/pom.xml $profile -pl nq-app -am -DskipTests install \
    && mvn -B -f backend/nq-app/pom.xml $profile -DskipTests package spring-boot:repackage
FROM eclipse-temurin:21-jre-jammy@sha256:affcab5356e1270ac35d2361ee83135af2f03e8d580eca78a6d5d6fdbf048db0
COPY release/docker/security/ /tmp/java-certificate-policy/
RUN sh /tmp/java-certificate-policy/install-certificate-policy.sh
ARG NQ_DISTRIBUTION=NQ_SELF_HOSTED
LABEL com.nexusquant.distribution=$NQ_DISTRIBUTION
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && useradd --system --uid 10001 nexusquant
WORKDIR /opt/nexusquant
COPY --from=build /source/backend/nq-app/target/nq-app-*.jar app.jar
ENV NQ_DISTRIBUTION=$NQ_DISTRIBUTION
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
