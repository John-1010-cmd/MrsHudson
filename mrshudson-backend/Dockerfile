# MrsHudson Backend Dockerfile
# 使用 Eclipse Temurin JDK 17 Alpine 版本
FROM eclipse-temurin:17-jdk-alpine

# 设置工作目录
WORKDIR /app

# 复制构建好的 JAR 文件
COPY target/*.jar app.jar

# 暴露应用端口
EXPOSE 8080

# 设置 JVM 参数（可根据需要调整）
ENV JAVA_OPTS="-Xms512m -Xmx1024m -Djava.security.egd=file:/dev/./urandom"

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
