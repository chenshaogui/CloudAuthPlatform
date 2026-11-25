#!/bin/sh
set -e

if [ -z "$JAVA_OPTS" ]; then
    JAVA_OPTS="-Xms${JAVA_XMS:-64m} -Xmx${JAVA_XMX:-256m}"
    JAVA_OPTS="$JAVA_OPTS -XX:MaxMetaspaceSize=${JAVA_METASPACE:-128m}"
    JAVA_OPTS="$JAVA_OPTS -XX:ReservedCodeCacheSize=${JAVA_CODE_CACHE:-48m}"
    JAVA_OPTS="$JAVA_OPTS -XX:MaxDirectMemorySize=${JAVA_DIRECT_MEMORY:-96m}"
    JAVA_OPTS="$JAVA_OPTS -Xss${JAVA_THREAD_STACK:-256k}"
    JAVA_OPTS="$JAVA_OPTS -XX:+UseSerialGC -Djava.security.egd=file:/dev/./urandom"
fi

java $JAVA_OPTS -Dspring.profiles.active="${SPRING_PROFILES_ACTIVE:-prod}" -jar /app/app.jar &
JAVA_PID=$!

nginx -g 'daemon off;' &
NGINX_PID=$!

trap 'kill -TERM "$NGINX_PID" "$JAVA_PID" 2>/dev/null || true' TERM INT

while kill -0 "$JAVA_PID" 2>/dev/null && kill -0 "$NGINX_PID" 2>/dev/null; do
    sleep 1
done

kill -TERM "$JAVA_PID" "$NGINX_PID" 2>/dev/null || true
wait "$JAVA_PID" 2>/dev/null || true
wait "$NGINX_PID" 2>/dev/null || true
