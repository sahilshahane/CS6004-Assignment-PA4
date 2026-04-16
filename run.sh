MAIN_CLASS=PA4

OUTPUT_DIR=class_outputs

rm -rf sootOutput
rm -rf "$OUTPUT_DIR"

mkdir -p "$OUTPUT_DIR"

javac -cp .:./lib/soot.jar -d "$OUTPUT_DIR" $MAIN_CLASS.java
javac -d "$OUTPUT_DIR/$1" testcases/$1/Test.java

java -cp "$OUTPUT_DIR":./lib/soot.jar $MAIN_CLASS $1
