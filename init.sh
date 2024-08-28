#!/bin/bash

SUBMODULE_DIR="hybrid-ads"
EXCEPT_LIB="#include <stdexcept>"

declare -a FILES_TO_MODIFY=(
    "lib/splitting_tree.hpp"
    "lib/reachability.cpp"
)


echo "Updating submodule..."
git submodule update --init

# Check if the file exists
if [[ -d "$SUBMODULE_DIR" ]]; then
    cd "$SUBMODULE_DIR" || exit

    for i in "${!FILES_TO_MODIFY[@]}"; do
        FILE="${FILES_TO_MODIFY[$i]}"

        if [[ -f "$FILE" ]]; then
            if ! grep -q "$EXCEPT_LIB" "$FILE"; then
                LINE=$(grep -n "#include" $FILE | tail -n 1 | cut -d ':' -f 1)
                sed -i "${LINE}i $EXCEPT_LIB" "$FILE"
            fi
        else
            echo "Error: File $FILE does not exist."
        fi
    done

    mkdir -p build
    cd build
    cmake -DCMAKE_BUILD_TYPE=RelWithDebInfo ..
    make
    
    echo "File modified successfully."
else
    echo "Error: File $FILE_TO_MODIFY does not exist."
    exit 1
fi

echo "Submodule update and file modification completed."
