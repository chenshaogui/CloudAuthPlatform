FROM eclipse-temurin:17-jre

LABEL maintainer="cloudauth"
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENV PARAMS=""
ENV TZ=Asia/Shanghai
ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update \
    && apt-get install -y --no-install-recommends tzdata curl \
    && rm -rf /var/lib/apt/lists/* \
    && ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

VOLUME /tmp
COPY *.jar app.jar
ENTRYPOINT [ "sh", "-c", "java -Djava.security.egd=file:/dev/./urandom $JAVA_OPTS -jar app.jar $PARAMS" ]
