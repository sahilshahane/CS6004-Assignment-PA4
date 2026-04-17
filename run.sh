MAIN_CLASS=PA4

OUTPUT_DIR=class_outputs

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
rm -rf "$OUTPUT_DIR"

mkdir -p "$OUTPUT_DIR"

javac -cp .:./lib/soot.jar -d "$OUTPUT_DIR" $MAIN_CLASS.java
javac testcases/$TEST_CASE/Test.java

if [ $COMPILE_ONLY -eq 0 ]; then
    java -cp "$OUTPUT_DIR":./lib/soot.jar $MAIN_CLASS $TEST_CASE
fi
