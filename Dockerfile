FROM eclipse-temurin:17-jdk AS builder

WORKDIR /workspace/app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY src src

RUN --mount=type=cache,target=/root/.m2 ./mvnw install -DskipTests
RUN mkdir -p target/dependency

WORKDIR /workspace/app/target/dependency

RUN jar -xf ../*.jar

FROM ubuntu:22.04

ARG DEPENDENCY=/workspace/app/target/dependency

ENV JAVA_HOME=/opt/java/openjdk
COPY --from=eclipse-temurin:17 $JAVA_HOME $JAVA_HOME
ENV PATH="${JAVA_HOME}/bin:${PATH}"

VOLUME /tmp

COPY --from=builder ${DEPENDENCY}/BOOT-INF/lib /app/lib
COPY --from=builder ${DEPENDENCY}/META-INF /app/META-INF
COPY --from=builder ${DEPENDENCY}/BOOT-INF/classes /app

COPY ./entrypoint.sh /entrypoint.sh
COPY ./profiles /profiles

ENTRYPOINT ["/entrypoint.sh"]
