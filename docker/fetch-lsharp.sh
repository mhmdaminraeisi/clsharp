#!/bin/sh
set -eu

if [ -f /source/baseline/lsharp/Cargo.toml ]; then
    cp /source/baseline/lsharp/Cargo.toml /source/baseline/lsharp/README.md .
    cp -R /source/baseline/lsharp/src /source/baseline/lsharp/benches .
else
    # Checked-in build sources let a plain clone build even when GitLab is down.
    (cd /source/baseline/lsharp-support && sha256sum --check archive.sha256)
    tar -xzf /source/baseline/lsharp-support/source.tar.gz
fi
