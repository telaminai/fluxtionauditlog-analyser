# usage: source gtenv.sh <m2 dir>
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH=$JAVA_HOME/bin:$PATH
mkdir -p "$1"; S=/private/tmp/fx-base13/settings-empty.xml; [ -f $S ] || echo '<settings/>' > $S
export MAVEN_OPTS="-Dmaven.repo.local=$1"
export MAVEN_ARGS="-s $S"
unset JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS CLASSPATH
