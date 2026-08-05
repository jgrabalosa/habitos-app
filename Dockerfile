FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY norday-motor/pom.xml  norday-motor/pom.xml
COPY habitos/pom.xml       habitos/pom.xml
COPY norday-server/pom.xml norday-server/pom.xml
COPY norday-motor/src  norday-motor/src
COPY habitos/src       habitos/src
COPY norday-server/src norday-server/src
RUN apk add --no-cache maven && mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# La app ya no llama a TimeZone.setDefault: cada cálculo de cara al usuario
# resuelve su zona, y los sellos de auditoría van en UTC. Lo que quede sin
# zona explícita no debe depender del host.
ENV TZ=UTC
COPY --from=build /app/norday-server/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar", "--server.port=${PORT:8080}"]
