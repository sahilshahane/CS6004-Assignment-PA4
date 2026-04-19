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
ENABLE_ANALYSIS=true
ENABLE_CHECK_INLINER=true
ENABLE_UNREACHABLE=true
ENABLE_INVOKE_METRICS=false

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
        --no-analysis)
            ENABLE_ANALYSIS=false
            shift
            ;;
        --no-check-inliner)
            ENABLE_CHECK_INLINER=false
            shift
            ;;
        --no-unreachable)
            ENABLE_UNREACHABLE=false
            shift
            ;;
        --invoke-metrics)
            ENABLE_INVOKE_METRICS=true
            shift
            ;;
        *)
            shift
            ;;
    esac
done

OUTPUT_DIR="class_outputs"

sudo rm -rf $SOOT_OUTPUT_DIR
sudo rm -rf editedClasses

if [ $NO_BUILD -eq 0 ]; then
    sudo rm -rf "$OUTPUT_DIR"
    mkdir -p "$OUTPUT_DIR"
    sudo rm -rf "$TEST_CASE/*.class"

    # Build the analysis tool
    javac -proc:none -cp ".:$LIB_CLASSPATH" -d "$OUTPUT_DIR" "$MAIN_CLASS.java" "AnalysisTransformer.java" "InvokeMetricCollector.java" "UnreachableMethodRemover.java" "MyRuntimeMetrics.java"

    # Copy MyRuntimeMetrics to test case folder so it becomes part of the application for Soot to process
    sudo cp "MyRuntimeMetrics.java" "$TEST_CASE/"
    
    # Build the testcase
    javac -cp ".:$OUTPUT_DIR:$LIB_CLASSPATH" "$TEST_CASE/Test.java" "$TEST_CASE/MyRuntimeMetrics.java"
fi


JSON_ARGS="{\"class_path\":\"$TEST_CASE\",\"output_dir\":\"$SOOT_OUTPUT_DIR\",\"enable_transform_analysis\":$ENABLE_ANALYSIS,\"enable_transform_check_inliner\":$ENABLE_CHECK_INLINER,\"enable_transform_unreachable\":$ENABLE_UNREACHABLE,\"enable_transform_invoke_metrics\":$ENABLE_INVOKE_METRICS}"

java -cp ".:$OUTPUT_DIR:$LIB_CLASSPATH" "$MAIN_CLASS" "$JSON_ARGS"


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