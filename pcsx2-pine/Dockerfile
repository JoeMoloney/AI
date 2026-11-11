FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pcsx2-pine/pom.xml pcsx2-pine/mvnw ./pcsx2-pine/
COPY pcsx2-pine/.mvn ./pcsx2-pine/.mvn
COPY pcsx2-pine/src ./pcsx2-pine/src
WORKDIR /build/pcsx2-pine
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/pcsx2-pine/target/pcsx2-pine-mcp-0.2.0-all.jar /app/server.jar
EXPOSE 18083
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/server.jar"]
