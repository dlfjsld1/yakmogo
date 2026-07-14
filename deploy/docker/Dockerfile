# syntax=docker/dockerfile:1
ARG JAR_NAME

FROM eclipse-temurin:21.0.11_10-jre-noble

ARG JAR_NAME
WORKDIR /app
COPY --chown=10001:10001 ${JAR_NAME} /app/yakmogo.jar

USER 10001:10001
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/yakmogo.jar"]
