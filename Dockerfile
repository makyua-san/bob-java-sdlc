# ビルドステージ
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Mavenをインストール
RUN apk add --no-cache maven

# 依存関係をダウンロード（キャッシュ活用）
COPY pom.xml .
RUN mvn dependency:go-offline

# ソースコードをコピーしてビルド
COPY src src
RUN mvn clean package -DskipTests

# 実行ステージ
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
COPY --from=builder /app/target/*.jar app.jar
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
EXPOSE 8080
