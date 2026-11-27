FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN ./mvnw -B dependency:go-offline
COPY src src
RUN ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --create-home appuser
COPY --from=build /app/target/*.jar app.jar
USER appuser
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]