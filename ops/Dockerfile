FROM eclipse-temurin:17-jre
ARG MODULE
COPY ${MODULE}/target/${MODULE}-0.1.0.jar /app/app.jar
WORKDIR /app
RUN mkdir -p /app/.local
ENV SERVER_ADDRESS=0.0.0.0
ENTRYPOINT ["java","-jar","/app/app.jar"]
