# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-17 AS backend-build
WORKDIR /build/backend
COPY backend-monolith/ ./
RUN mvn -B -DskipTests package

FROM node:20-alpine AS frontend-build
WORKDIR /build/frontend
COPY frontend/package.json ./
RUN npm install --no-audit --no-fund
COPY frontend/ ./
RUN npm run build:prod

FROM eclipse-temurin:17-jre-alpine

ENV TZ=Asia/Shanghai \
    SPRING_PROFILES_ACTIVE=local \
    JAVA_OPTS="-Xms64m -Xmx256m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m -XX:MaxDirectMemorySize=96m -Xss256k -XX:+UseSerialGC -Djava.security.egd=file:/dev/./urandom"

RUN apk add --no-cache nginx tzdata fontconfig font-wqy-zenhei \
    && ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && mkdir -p /var/cache/nginx /var/log/nginx

COPY --from=backend-build /build/backend/target/cloud-auth-platform-monolith-2.5.1.jar /app/app.jar
COPY --from=frontend-build /build/frontend/cloudAuthPlatform/ /usr/share/nginx/html/cloudAuthPlatform/
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY docker/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=90s --retries=3 \
    CMD wget -q --spider http://127.0.0.1/cloudAuthPlatform/ || exit 1

ENTRYPOINT ["/entrypoint.sh"]
