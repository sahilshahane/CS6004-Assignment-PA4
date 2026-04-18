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

OUTPUT_DIR="class_outputs"
SOOT_OUTPUT_DIR="sootOutput"

rm -rf $SOOT_OUTPUT_DIR
rm -rf "$OUTPUT_DIR"
rm -rf editedClasses
mkdir -p "$OUTPUT_DIR"

rm -rf "testcases/$TEST_CASE/*.class"

# Build the analysis tool
javac -proc:none -cp ".:$LIB_CLASSPATH" -d "$OUTPUT_DIR" "$MAIN_CLASS.java" "AnalysisTransformer.java"

# Build the testcase
javac -cp ".:$OUTPUT_DIR:$LIB_CLASSPATH" "testcases/$TEST_CASE/Test.java"

if [ $COMPILE_ONLY -eq 0 ]; then
    java -cp ".:$OUTPUT_DIR:$LIB_CLASSPATH" "$MAIN_CLASS" "$TEST_CASE"
fi

if [ $? -eq 0 ]; then
    echo "Converting jimple to class files  \n"
    java -cp ".:$LIB_CLASSPATH" soot.Main -src-prec jimple -f class -process-dir $SOOT_OUTPUT_DIR -output-dir editedClasses
else
    echo "\n\nError: Failed to run soot modifications. Aborting execution."
    exit
fi


# Check if the previous command successfully compiled the files
if [ $? -eq 0 ]; then
    echo -e "Running the modified code\n"
    java -cp editedClasses Test
else
    echo "\n\nError: Failed to convert jimple to class files. Aborting execution."
    exit
fi