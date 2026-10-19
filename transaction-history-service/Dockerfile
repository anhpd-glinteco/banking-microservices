FROM maven:3.8.3-openjdk-17 AS build
WORKDIR /app

# Copy and build bank-common first
COPY bank-common /app/bank-common
WORKDIR /app/bank-common
RUN mvn clean install -DskipTests

# Build transaction-history-service
WORKDIR /app/transaction-history-service
COPY transaction-history-service/pom.xml .
COPY transaction-history-service/src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17.0.8.1_1-jre-ubi9-minimal
RUN unlink /etc/localtime;ln -s  /usr/share/zoneinfo/Asia/Ho_Chi_Minh /etc/localtime

COPY --from=build /app/transaction-history-service/target/transaction-history-service-0.0.1-SNAPSHOT.jar /run/transaction-history-service-0.0.1-SNAPSHOT.jar

EXPOSE 8084
ENV JAVA_OPTIONS="-Xmx2048m -Xms256m"
ENTRYPOINT ["java", "-jar", "/run/transaction-history-service-0.0.1-SNAPSHOT.jar"]
