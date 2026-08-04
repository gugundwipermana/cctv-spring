# Stage 1: build jar dengan Maven
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy pom.xml dulu supaya layer dependency di-cache selama pom.xml
# tidak berubah (rebuild jadi jauh lebih cepat saat cuma ubah kode Java)
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# Stage 2: image runtime, JRE + ffmpeg (dipakai RecordingService buat compile video)
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache ffmpeg
WORKDIR /app

COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
