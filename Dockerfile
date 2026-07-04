FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system kcals && useradd --system --gid kcals kcals
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
USER kcals
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
