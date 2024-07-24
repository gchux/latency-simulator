FROM eclipse-temurin:17-jdk as build
WORKDIR /workspace/app
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw install -DskipTests
RUN mkdir -p target/dependency && (cd target/dependency; jar -xf ../*.jar)

FROM ubuntu

ENV JAVA_HOME=/opt/java/openjdk
COPY --from=eclipse-temurin:17 $JAVA_HOME $JAVA_HOME
ENV PATH="${JAVA_HOME}/bin:${PATH}"

VOLUME /tmp

ARG DEPENDENCY=/workspace/app/target/dependency

COPY --from=build ${DEPENDENCY}/BOOT-INF/lib /app/lib
COPY --from=build ${DEPENDENCY}/META-INF /app/META-INF
COPY --from=build ${DEPENDENCY}/BOOT-INF/classes /app

COPY ./profiles /profiles
COPY ./glowroot.json /glowroot/admin.json
COPY ./bin/glowroot.jar /glowroot/glowroot.jar
COPY ./bin/glowroot/lib/glowroot-embedded-collector.jar /glowroot/lib/glowroot-embedded-collector.jar

ENTRYPOINT ["java", "-javaagent:/glowroot/glowroot.jar", "-cp", "/glowroot/lib/*:app:app/lib/*", "dev.chux.gcp.crun.Application"]
