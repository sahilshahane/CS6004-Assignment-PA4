MAIN_CLASS=PA4
LIB_CLASSPATH="./lib/*"

TEST_CASE=$1
shift

if [ -z "$TEST_CASE" ]; then
    echo "Error: Please provide a test case name (e.g., testcases/Test1)"
    exit 1
fi

COMPILE_ONLY=0
NO_BUILD=0
SOOT_OUTPUT_DIR="sootOutput"
while [ "$#" -gt 0 ]; do
    case "$1" in
        --compile-only)
            COMPILE_ONLY=1
            shift
            ;;
        --no-build)
            NO_BUILD=1
            shift
            ;;
        --out-dir)
            SOOT_OUTPUT_DIR="$2"
            shift 2
            ;;
        *)
            shift
            ;;
    esac
done

OUTPUT_DIR="class_outputs"

rm -rf $SOOT_OUTPUT_DIR
rm -rf editedClasses

if [ $NO_BUILD -eq 0 ]; then
    rm -rf "$OUTPUT_DIR"
    mkdir -p "$OUTPUT_DIR"
    rm -rf "$TEST_CASE/*.class"

    # Build the analysis tool
    javac -proc:none -cp ".:$LIB_CLASSPATH" -d "$OUTPUT_DIR" "$MAIN_CLASS.java" "AnalysisTransformer.java"

    # Build the testcase
    javac -cp ".:$OUTPUT_DIR:$LIB_CLASSPATH" "$TEST_CASE/Test.java"
fi


java -cp ".:$OUTPUT_DIR:$LIB_CLASSPATH" "$MAIN_CLASS" "$TEST_CASE" "$SOOT_OUTPUT_DIR"


# if [ $? -eq 0 ]; then
#     echo "\nSoot processing complete\n" 
#     echo "Converting jimple to class files  \n"
#     java -cp ".:$LIB_CLASSPATH" soot.Main -src-prec jimple -f class -process-dir $SOOT_OUTPUT_DIR -output-dir editedClasses
# else
#     echo "\n\nError: Failed to run soot modifications. Aborting execution."
#     exit
# fi

# Check if the previous command successfully compiled the files
if [ $? -eq 0 ]; then
    echo "\nSoot processing complete\n" 

    if [ $COMPILE_ONLY -eq 0 ]; then
        echo "Running the modified code\n"
        java -cp $SOOT_OUTPUT_DIR Test
    fi
else
    echo "\n\nError: Failed to run soot modifications. Aborting execution."
    exit
fi