#!/bin/bash

TEST_CASE=$1

if [ -z "$TEST_CASE" ]; then
    echo "Error: Please provide a test case name (e.g., testcases/Test1)"
    exit 1
fi

RUN=1
echo "========== RUN $RUN: $TEST_CASE =========="
TEST_NAME=$(basename "$TEST_CASE")
mkdir -p "iterative_runs/$TEST_NAME"
rm -rf "iterative_runs/$TEST_NAME"/*

./run.sh "$TEST_CASE" --compile-only
rm -rf sootOutput_iterative
mv sootOutput sootOutput_iterative
cp -r sootOutput_iterative "iterative_runs/$TEST_NAME/output_run$RUN"
mkdir -p "perf_result"
# get time to run the test case without soot optimization


while true; do
    RUN=$((RUN + 1))

    if [ $RUN -gt 10 ]; then
        echo "--> Max iterations (10) reached. Stopping."
        cp -r sootOutput_iterative sootOutput
        rm -rf sootOutput_iterative
        break
    fi

    echo ""
    echo "========== RUN $RUN: sootOutput_iterative =========="
    ./run.sh "sootOutput_iterative" --compile-only --no-build

    cp -r sootOutput "iterative_runs/$TEST_NAME/output_run$RUN"

    if diff -rq sootOutput sootOutput_iterative > /dev/null; then
        echo "--> Fixpoint reached! No new changes found in run $RUN."
        rm -rf sootOutput_iterative
        break
    else
        echo "--> Changes detected. Iterating again..."
        rm -rf sootOutput_iterative
        mv sootOutput sootOutput_iterative
    fi
done

echo ""
echo "========== FINAL EXECUTION =========="
echo "Running the final compiled code"
# record the time to run the final compiled code
# /usr/bin/time -v java -Xint -cp sootOutput Test 
# java -cp sootOutput Test 

TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

./run_vanila.sh $TEST_CASE "perf_result/${TEST_NAME}_final_${TIMESTAMP}.txt"

echo 0 > /proc/sys/kernel/nmi_watchdog
# perf stat -e cycles,instructions,cache-misses java -Xint -cp $TEST_CASE Test 
# store perf stat output in a file , perf_result/Timestamp.txt
# each iteration 
# mkdir -p $TIMESTAMP
# append to the file 
echo "Optimized TEST CASE: $TEST_CASE" >> "perf_result/${TEST_NAME}_final_${TIMESTAMP}.txt"
perf stat -d -d -r 5 java -Xint -cp sootOutput Test |& tee -a "perf_result/${TEST_NAME}_final_${TIMESTAMP}.txt"
echo 1 > /proc/sys/kernel/nmi_watchdog
