FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven && mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# La app ya no llama a TimeZone.setDefault: cada cálculo de cara al usuario
# resuelve su zona, y los sellos de auditoría van en UTC. Lo que quede sin
# zona explícita no debe depender del host.
ENV TZ=UTC
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar", "--server.port=${PORT:8080}"]