#!/bin/sh
set -eu

JAR_PATH=/home/pi/myprojects/yakmogo-enhancement/yakmogo-0.0.7-SNAPSHOT.jar

exec /usr/bin/java -jar "$JAR_PATH"
