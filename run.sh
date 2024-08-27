#!/bin/bash
file="1"

for i in {1..100}; do
    ./gradlew run --args="$file"
    echo "Running $i ..."
done
