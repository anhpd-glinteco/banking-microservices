FROM maven:3.8.3-openjdk-17 AS build
WORKDIR /app

# Copy and build bank-common first
COPY bank-common /app/bank-common
WORKDIR /app/bank-common
RUN mvn clean install -DskipTests

# Build account-service
WORKDIR /app/account-service
COPY account-service/pom.xml .
COPY account-service/src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17.0.8.1_1-jre-ubi9-minimal
RUN unlink /etc/localtime;ln -s  /usr/share/zoneinfo/Asia/Ho_Chi_Minh /etc/localtime

COPY --from=build /app/account-service/target/account-service-0.0.1-SNAPSHOT.jar /run/account-service-0.0.1-SNAPSHOT.jar

EXPOSE 8081
ENV JAVA_OPTIONS="-Xmx2048m -Xms256m"
ENTRYPOINT ["java", "-jar", "/run/account-service-0.0.1-SNAPSHOT.jar"]
