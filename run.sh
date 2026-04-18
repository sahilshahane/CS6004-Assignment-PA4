MAIN_CLASS=PA4
LIB_CLASSPATH="./lib/*"

COMPILE_ONLY=0
if [ "$1" = "--compile-only" ]; then
    COMPILE_ONLY=1
    shift
fi

TEST_CASE=$1

if [ -z "$TEST_CASE" ]; then
    echo "Error: Please provide a test case name (e.g., Test1)"
    exit 1
fi

rm -rf sootOutput

# Build the analysis tool using Maven
mvn clean compile -q

# Build the testcase
javac -cp ".:target/classes:$LIB_CLASSPATH" "testcases/$TEST_CASE/Test.java"

if [ $COMPILE_ONLY -eq 0 ]; then
    java -cp "target/classes:$LIB_CLASSPATH" "$MAIN_CLASS" "$TEST_CASE"
fi