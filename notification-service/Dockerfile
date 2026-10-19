FROM maven:3.8.3-openjdk-17 AS build
WORKDIR /app

# Copy and build bank-common first
COPY bank-common /app/bank-common
WORKDIR /app/bank-common
RUN mvn clean install -DskipTests

# Build notification-service
WORKDIR /app/notification-service
COPY notification-service/pom.xml .
COPY notification-service/src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17.0.8.1_1-jre-ubi9-minimal
RUN unlink /etc/localtime;ln -s  /usr/share/zoneinfo/Asia/Ho_Chi_Minh /etc/localtime

COPY --from=build /app/notification-service/target/notification-service-0.0.1-SNAPSHOT.jar /run/notification-service-0.0.1-SNAPSHOT.jar

EXPOSE 8083
ENV JAVA_OPTIONS="-Xmx2048m -Xms256m"
ENTRYPOINT ["java", "-jar", "/run/notification-service-0.0.1-SNAPSHOT.jar"]
