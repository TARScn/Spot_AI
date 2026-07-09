FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

ENV JAVA_OPTS="-Xms256m -Xmx768m"
ENV SPRING_PROFILES_ACTIVE=prod

COPY --from=build /workspace/target/*.jar /app/spotai.jar

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/spotai.jar"]
