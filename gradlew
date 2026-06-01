#!/bin/sh

#
# Gradle wrapper script
#

# Determine the absolute path to the project root
PRG="$0"
while [ -h "$PRG" ] ; do
    ls=$( ls -ld -- "$PRG" )
    link=${ls#*' -> '}
    case $link in
      /*)   PRG="$link" ;;
      *)    PRG="$( dirname "$PRG" )/$link" ;;
    esac
done
APP_HOME="$( cd "$( dirname "$PRG" )" > /dev/null && pwd -P )" || exit 1

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Find java
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

# Execute
exec "$JAVACMD" \
    $DEFAULT_JVM_OPTS \
    $JAVA_OPTS \
    $GRADLE_OPTS \
    "-Dorg.gradle.appname=gradlew" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
