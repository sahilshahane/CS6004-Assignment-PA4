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
sudo rm -rf "iterative_runs/$TEST_NAME"/*

sudo ./run.sh "$TEST_CASE" --compile-only
sudo rm -rf sootOutput_iterative
sudo mv sootOutput sootOutput_iterative
sudo cp -r sootOutput_iterative "iterative_runs/$TEST_NAME/output_run$RUN"
mkdir -p "perf_result/$TEST_NAME"
# get time to run the test case without soot optimization


while true; do
    RUN=$((RUN + 1))

    if [ $RUN -gt 15 ]; then
        echo "--> Max iterations (15) reached. Stopping."
        sudo cp -r sootOutput_iterative sootOutput
        sudo rm -rf sootOutput_iterative
        break
    fi

    echo ""
    echo "========== RUN $RUN: sootOutput_iterative =========="
    sudo ./run.sh "sootOutput_iterative" --compile-only --no-build

    sudo cp -r sootOutput "iterative_runs/$TEST_NAME/output_run$RUN"

    if diff -rq sootOutput sootOutput_iterative > /dev/null; then
        echo "--> Fixpoint reached! No new changes found in run $RUN."
        sudo rm -rf sootOutput_iterative
        break
    else
        echo "--> Changes detected. Iterating again..."
        sudo rm -rf sootOutput_iterative
        sudo mv sootOutput sootOutput_iterative
    fi
done

TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

echo ""
echo "========== COLLECTING INVOKE METRICS (BASELINE) =========="
sudo ./run.sh "$TEST_CASE" --compile-only --no-analysis --no-check-inliner --no-unreachable --invoke-metrics --out-dir "sootOutput_baseline_metrics"
sudo sh -c "java -cp sootOutput_baseline_metrics Test | grep -E '^(Static|Instance) Calls:' | tee \"perf_result/$TEST_NAME/baseline_invoke_metrics_${TIMESTAMP}.txt\""
echo "=========================================================="

echo ""
echo "========== COLLECTING INVOKE METRICS (OPTIMIZED) =========="
sudo ./run.sh "sootOutput" --compile-only --no-build --no-analysis --no-check-inliner --no-unreachable --invoke-metrics --out-dir "sootOutput_metrics"
sudo sh -c "java -cp sootOutput_metrics Test | grep -E '^(Static|Instance) Calls:' | tee \"perf_result/$TEST_NAME/optimized_invoke_metrics_${TIMESTAMP}.txt\""
echo "==========================================================="

echo ""
echo "========== FINAL EXECUTION =========="
echo "Running the final compiled code"
# record the time to run the final compiled code
# /usr/bin/time -v java -Xint -cp sootOutput Test 
# java -cp sootOutput Test 

ORIG_PARANOID=$(cat /proc/sys/kernel/perf_event_paranoid)
sudo sh -c 'echo -1 > /proc/sys/kernel/perf_event_paranoid'

sudo ./run_vanila.sh $TEST_CASE "perf_result/$TEST_NAME/baseline_perf_results_${TIMESTAMP}.txt" "perf_result/$TEST_NAME/baseline_program_output_${TIMESTAMP}.txt"

sudo sh -c 'echo 0 > /proc/sys/kernel/nmi_watchdog'
# perf stat -e cycles,instructions,cache-misses java -Xint -cp $TEST_CASE Test 
# store perf stat output in a file , perf_result/Timestamp.txt
# each iteration 
# mkdir -p $TIMESTAMP
# append to the file 
sudo sh -c "echo 'Optimized TEST CASE: $TEST_CASE' >> perf_result/$TEST_NAME/optimized_perf_results_${TIMESTAMP}.txt"
sudo sh -c "perf stat -x, -d -d -r 5 -o perf_result/$TEST_NAME/optimized_perf_results_${TIMESTAMP}.txt --append java -Xint -cp sootOutput Test > perf_result/$TEST_NAME/optimized_program_output_${TIMESTAMP}.txt 2>&1"
sudo sh -c 'echo 1 > /proc/sys/kernel/nmi_watchdog'

sudo sh -c "echo $ORIG_PARANOID > /proc/sys/kernel/perf_event_paranoid"
