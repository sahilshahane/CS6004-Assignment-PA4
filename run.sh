MAIN_CLASS=PA4
OUTPUT_DIR=class_outputs
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
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

javac -proc:none -cp ".:$LIB_CLASSPATH" -d "$OUTPUT_DIR" "$MAIN_CLASS.java" "AnalysisTransformer_boomerang.java"
javac -cp ".:$OUTPUT_DIR:$LIB_CLASSPATH" "testcases/$TEST_CASE/Test.java"

if [ $COMPILE_ONLY -eq 0 ]; then
    java -cp "$OUTPUT_DIR:$LIB_CLASSPATH" "$MAIN_CLASS" "$TEST_CASE"
fi