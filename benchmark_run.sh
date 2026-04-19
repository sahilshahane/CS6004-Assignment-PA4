# for b in $(java -jar lib/dacapo-9.12-MR1-bach.jar -l); do
#   echo "=== $b ==="
#   java -jar lib/dacapo-9.12-MR1-bach.jar -s small -n 1 "$b" 2>&1 | grep -E "(PASSED|FAILED)"
# done


for b in $(java -jar lib/dacapo-9.12-MR1-bach.jar -l); do
  result=$(java -jar lib/dacapo-9.12-MR1-bach.jar -s small -n 1 "$b" 2>&1 | grep -E "PASSED|FAILED")
  
  if echo "$result" | grep -q PASSED; then
    echo "$b PASSED"
  elif echo "$result" | grep -q FAILED; then
    echo "$b FAILED"
  else
    echo "$b UNKNOWN"
  fi
done