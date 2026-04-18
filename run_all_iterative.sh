#!/bin/bash

# Check if the testcases directory exists
if [ ! -d "testcases" ]; then
    echo "Error: testcases directory not found."
    exit 1
fi

# Iterate over all directories inside testcases/
for test_dir in testcases/*/; do
    # Extract just the folder name (e.g., Test1)
    test_case=$(basename "$test_dir")
    
    echo "======================================================"
    echo " Executing: $test_case"
    echo "======================================================"
    
    # Forward any CLI flags (like --run) and append the test case path
    ./run_iterative.sh "testcases/$test_case"
    
    echo ""
done

echo "Finished running all test cases!"
