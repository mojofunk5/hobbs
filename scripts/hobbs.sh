#!/bin/bash

JAR="build/libs/hobbs-0.0.1-SNAPSHOT.jar"
PID_FILE="hobbs.pid"
PORT="${2:-8080}"

LOG_FILE="hobbs.log"

start() {
    if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
        echo "Already running (pid $(cat "$PID_FILE"))"
        exit 1
    fi
    java -jar "$JAR" "$PORT" >> "$LOG_FILE" 2>&1 &
    echo $! > "$PID_FILE"
    echo "Started on port $PORT (pid $!), logging to $LOG_FILE"
}

stop() {
    if [ ! -f "$PID_FILE" ]; then
        echo "Not running (no pid file)"
        exit 1
    fi
    PID=$(cat "$PID_FILE")
    if kill -0 "$PID" 2>/dev/null; then
        kill "$PID"
        rm "$PID_FILE"
        echo "Stopped (pid $PID)"
    else
        echo "Not running (stale pid $PID)"
        rm "$PID_FILE"
        exit 1
    fi
}

status() {
    if [ ! -f "$PID_FILE" ]; then
        echo "Not running"
        exit 1
    fi
    PID=$(cat "$PID_FILE")
    if kill -0 "$PID" 2>/dev/null; then
        echo "Running (pid $PID)"
    else
        echo "Not running (stale pid $PID)"
        exit 1
    fi
}

restart() {
    stop
    start
}

case "$1" in
    start)   start ;;
    stop)    stop ;;
    restart) restart ;;
    status)  status ;;
    *)       echo "Usage: $0 {start|stop|restart|status} [port]"; exit 1 ;;
esac
