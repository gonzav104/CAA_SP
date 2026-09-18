# syntax=docker/dockerfile:1

# ---------------------------------------------------------------
# Stage 1: build
# Se copia primero el wrapper y el pom para que la resolucion de
# dependencias quede cacheada y no se repita ante cambios de codigo.
# ---------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -B clean package -DskipTests

# ---------------------------------------------------------------
# Stage 2: runtime
# Imagen JRE minima y usuario sin privilegios.
# ---------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S caa && adduser -S caa -G caa

COPY --from=build --chown=caa:caa /build/target/api-*.jar app.jar

USER caa

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
