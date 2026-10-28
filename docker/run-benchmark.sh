#!/bin/sh
set -eu

# Expose the unchanged upstream Rust CLI for options outside the common runner.
if [ "${1-}" = "--native" ]; then
    if [ -z "${LSHARP_RUST_EXECUTABLE-}" ]; then
        echo '--native is available only in the original Rust L# image' >&2
        exit 2
    fi
    shift
    mkdir -p "$BENCHMARK_OUTPUT/native/log" "$BENCHMARK_OUTPUT/native/resumable_config"
    cd "$BENCHMARK_OUTPUT/native"
    exec "$LSHARP_RUST_EXECUTABLE" "$@"
fi

# A manifest can be the first argument; otherwise use this image's model cohort.
if [ "${1-}" = "--help" ] && [ "$#" -eq 1 ]; then
    printf 'Image defaults: models=%s; algorithms=%s; output=%s\n' \
        "$BENCHMARK_MODELS" "$BENCHMARK_ALGORITHMS" "$BENCHMARK_OUTPUT"
    printf 'Common runner options (image defaults take precedence):\n'
    set -- --help
else
    case "${1-}" in
        ""|--*) set -- "$BENCHMARK_MODELS" "$@" ;;
    esac
    has_algorithms=false
    has_output=false
    for argument in "$@"; do
        case "$argument" in
            --algorithms) has_algorithms=true ;;
            --output-dir) has_output=true ;;
        esac
    done
    if [ "$has_algorithms" = false ]; then
        set -- "$@" --algorithms "$BENCHMARK_ALGORITHMS"
    fi
    if [ "$has_output" = false ]; then
        set -- "$@" --output-dir "$BENCHMARK_OUTPUT"
    fi
fi

exec java -DLOG_LEVEL=WARN '-Dclstar.classpath=/opt/nclsharp/clstar/lib/*' \
    -cp '/opt/nclsharp/lib/*' institute.teias.Benchmark "$@"
