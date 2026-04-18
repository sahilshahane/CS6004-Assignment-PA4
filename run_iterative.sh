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

while true; do
    RUN=$((RUN + 1))
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
java -cp sootOutput Test
