FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY x360/recomp-mcp/pom.xml x360/recomp-mcp/mvnw ./x360/recomp-mcp/
COPY x360/recomp-mcp/.mvn ./x360/recomp-mcp/.mvn
COPY x360/recomp-mcp/src ./x360/recomp-mcp/src
WORKDIR /build/x360/recomp-mcp
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/x360/recomp-mcp/target/x360-recomp-mcp-0.1.0-all.jar /app/server.jar
EXPOSE 18084
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/server.jar"]
