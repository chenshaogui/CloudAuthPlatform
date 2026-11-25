#!/bin/sh
set -e

java $JAVA_OPTS -Dspring.profiles.active="${SPRING_PROFILES_ACTIVE:-local}" -jar /app/app.jar &
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
