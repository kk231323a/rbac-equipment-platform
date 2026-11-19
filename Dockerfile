from eclipse-temurin:17-jre-jammy
workdir /app
copy target/rbac-system-0.0.1-SNAPSHOT.jar app.jar
expose 8080
entrypoint ["java", "-Duser.timezone=Asia/Shanghai", "-jar", "app.jar"]