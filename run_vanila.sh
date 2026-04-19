TEST_CASE=$1
# PERF_OUTPUT_DIR="perf_result"
PERF_OUTPUT_FILE=$2

if [ -z "$TEST_CASE" ]; then
    echo "Error: Please provide a test case name (e.g., testcases/Test1)"
    exit 1
fi  
if [ -z "$TEST_CASE" ]; then
    echo "Error: Please provide a test case name (e.g., testcases/Test1)"
    exit 1
fi

# print the test case being run
echo "Running test case vanila version: $TEST_CASE"

# compile the test case without soot optimization
javac $TEST_CASE/*.java

TEST_NAME=$(basename "$TEST_CASE")
echo "========== RUN VANILA: $TEST_NAME =========="



# run with -Xint to not run the JIT compiler and get a more accurate time measurement
echo 0 > /proc/sys/kernel/nmi_watchdog
# perf stat -e cycles,instructions,cache-misses java -Xint -cp $TEST_CASE Test 
echo "Vanila TEST CASE: $TEST_CASE" >> "$PERF_OUTPUT_FILE"
# take the average of 5 runs to get a more accurate time measurement
perf stat -d -d -r 5 java -Xint -cp $TEST_CASE Test |& tee -a "$PERF_OUTPUT_FILE"

echo 1 > /proc/sys/kernel/nmi_watchdog