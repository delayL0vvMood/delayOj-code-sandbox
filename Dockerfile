# 使用官方的 Java 8 作为基础镜像
FROM openjdk:8-jdk-alpine

# 设置工作目录
WORKDIR /app

# 复制 Maven 项目的 pom.xml 文件到工作目录
COPY pom.xml .

# 复制 Maven 项目的源代码到工作目录
COPY src ./src
COPY tempCode ./tempCode

# 配置 Maven 使用淘宝镜像
RUN echo "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" > /tmp/settings.xml && \
    echo "<settings xmlns=\"http://maven.apache.org/SETTINGS/1.0.0\"" >> /tmp/settings.xml && \
    echo "          xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" >> /tmp/settings.xml && \
    echo "          xsi:schemaLocation=\"http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd\">" >> /tmp/settings.xml && \
    echo "    <mirrors>" >> /tmp/settings.xml && \
    echo "        <mirror>" >> /tmp/settings.xml && \
    echo "            <id>aliyunmaven</id>" >> /tmp/settings.xml && \
    echo "            <mirrorOf>central</mirrorOf>" >> /tmp/settings.xml && \
    echo "            <url>https://maven.aliyun.com/repository/public</url>" >> /tmp/settings.xml && \
    echo "        </mirror>" >> /tmp/settings.xml && \
    echo "    </mirrors>" >> /tmp/settings.xml && \
    echo "</settings>" >> /tmp/settings.xml

# 安装 Maven
RUN apk add --no-cache maven && \
    mvn -s /tmp/settings.xml dependency:go-offline

# 构建项目
RUN mvn -s /tmp/settings.xml clean package

# 使用 docker-maven-plugin 构建 Docker 镜像
RUN mvn -s /tmp/settings.xml docker:build

# 暴露 Spring Boot 应用的默认端口
EXPOSE 8081

# 复制生成的 JAR 文件
COPY target/delayoj-code-sandbox-0.0.1-SNAPSHOT.jar /app/delayoj-code-sandbox.jar

# 运行 Spring Boot 应用
CMD ["java", "-jar", "/app/delayoj-code-sandbox.jar"]