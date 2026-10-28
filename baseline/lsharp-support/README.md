# Original Rust L# baseline in Docker

The original project is a Git submodule at `baseline/lsharp`, using the canonical
URL <https://gitlab.science.ru.nl/sws/lsharp.git>. It is pinned to cached upstream
`origin/master` revision `c5593e341c5796afd620a922900eade2ac8413c1` (2025-01-01).
This checkout was seeded from an existing clone because GitLab was unreachable;
no local changes from that clone were imported. The submodule's tracked source is
unchanged. Its package declares `MIT OR Apache-2.0`; upstream authorship and
package metadata are preserved in its Cargo.toml and README.

This is a reproducible reference revision, not a claim that it is the exact
historical binary used for the paper. The TACAS 2022 archive remains available at
<https://doi.org/10.5281/zenodo.5735533>.

## Build (host needs Docker only)

From the repository root:

```bash
docker build -f Dockerfile.lsharp -t lsharp .
```

Rust, Cargo, linker tools and crate dependencies are handled by Docker. The Rust
builder uses **1.98.1**, pinned by version and image digest, with `--locked`.
`Cargo.lock` here is the frozen dependency resolution from the existing checkout;
upstream does not track a lockfile. Crate checksums are included. The lock requires
a newer compiler than the upstream manifest's original minimum of 1.71.
`source.sha256` checks the copied source/manifest against this pinned revision.
The final binary is statically linked and stripped. The final image builds the
shared Java benchmark adapter and does not ship a Rust SDK.

The first build requires network access for images and crates. The default Rust
base uses the Docker Official Images mirror on public ECR. Override `RUST_IMAGE`
with a build argument if needed. The Java/native prerequisites are built in this same Dockerfile. There is no
dependency on a previously built project image. After building, runs need no network.

A populated `baseline/lsharp` is used directly after checksum verification; otherwise
the build extracts `source.tar.gz` from this directory, checking `archive.sha256`
and then the individual source checksums. The archive contains only unchanged
Cargo.toml, README, Rust source and benchmark declarations from the pinned revision
(no model suite, results or nested submodules). Its optional nested
LearnLib/Hybrid-ADS submodules are not used and are not fetched.
The full submodule remains available for development; `git archive` of the parent
alone omits its contents. The build-source snapshot keeps Docker builds usable
from such an archive.

This integration was tested in Linux/amd64 Docker containers. Upstream requires
a 64-bit platform; other architectures have not been validated here.

## Model cohort

`benchmarks/lsharp-models.txt` is exactly the intersection of `paper-models.txt`
with original model sizes **100 <= size <= 300**, read from `test_results.csv`.
It contains 30 models, `65.dot` through `94.dot`, with actual sizes 112–288.
Original filenames are preserved. Models are read from the shared
`src/main/resources/all_FSMs` directory; no model copies are added to the submodule.

## Run

Full L# subset, ten successful repetitions per model (300 rows):

```bash
mkdir -p results/lsharp
docker run --name lsharp-rust-full --memory=4g --cpus=2 -v "$(pwd)/results:/results" lsharp
```

One-repetition subset run (30 trials):

```bash
mkdir -p results/smoke/lsharp
docker run --rm --memory=4g --cpus=2 -v "$(pwd)/results:/results" lsharp benchmarks/lsharp-models.txt --algorithms lsharp-rust --repetitions 1 --seed 2027 --output-dir /results/smoke/lsharp
python3 scripts/check_results.py results/smoke/lsharp > results/smoke/lsharp/validation.json
```

For a single-model smoke test, mount a one-line manifest at `/models.txt` and use
that path instead of `benchmarks/lsharp-models.txt`. Use fresh output directories.
The common `--resume-from` option imports completed trials into a new directory;
mount previous output read-only at `/previous` and pass `--resume-from /previous`.
It does not resume a partially executed individual learning trial. Resume checks
include model hashes, binary hash, rules, seeds and the Rust oracle settings.

The main image's `--algorithms lsharp` still means the **local Java** implementation;
`lsharp-rust` means the **original Rust** implementation in this image.

The real two-model smoke test used `65.dot` (112 states) and `94.dot` (288 states),
one trial each, and completed successfully under a 4 GiB / 2 CPU Docker limit.
Measured elapsed times were about 22 seconds and 267 seconds respectively;
these are observations for one seed, not full-cohort runtime estimates.

## Configuration and cost accounting

The unchanged upstream CLI is invoked with:

```text
-e hads-int --rule2 ads --rule3 ads -k 10 -l 10 --eq-mode infinite -v 0 -q
```

`hads-int` is upstream's internal Rust implementation of Hybrid-ADS. It does not
invoke the shared C++ executable. Upstream checks reference equivalence before
searching for a counterexample; it never substitutes a reference CE when HADS is
selected. The same HADS family and k/length values do not imply identical test
streams/budgets across Rust and Java/CL* implementations.

`--rust-extra-states`, `--rust-random-length`, `--rust-cache-rules` and
`--rust-threads` are recorded and configurable. Defaults are 10, 10, false and 2.
The experimental upstream `--compress-tree` and optional external FSMLib oracles
are not enabled. `--cache` / `--heap-mb` apply to Java learners, not the Rust
process; Docker's memory limit bounds the native Rust run. The common outer
`--timeout-seconds` defaults to 1200 per attempt. At most 20 attempts are allowed
per model by default; failed or timed-out trials are logged and do not become
success rows. Counts/learning success are taken from the original CLI output;
its `learned=true` follows its internal reference-equivalence check.

The common signed trial seed is passed as its unsigned 32-bit value to Rust's
u64 seed option. `nativeSeed` records that actual value. The partialSeed ledger
column is unused for this non-compositional learner. Fixed seeds are recorded;
bit-for-bit reproducibility across implementations is not claimed.

Outputs under the mounted directory:

- `lsharp-rust_results.csv`: `file,size,learnSymbols,learnResets,TestSymbols,TestResets`.
- `attempts.csv`: every attempt, seed, elapsed time, status and log path.
- `attempts/<trial>.properties`: raw counters and `counterConvention=upstream-unadjusted`.
- `attempts/<trial>/upstream.csv`: untouched original Rust CSV, including its
  hypothesis-state count, rounds, ADS score and algorithm identifier.
- `attempts/<trial>/log`, `hypothesis`, `resumable_config`: isolated upstream files;
  the runner never overwrites files in the submodule source checkout.
- `run.properties`, `models.txt`, `inputs.sha256`: configuration and provenance,
  including source revision, toolchain, Cargo.lock hash and binary hash.

The summary size is the input model size; upstream `num_states` describes the
learned hypothesis and is kept separately. Rust learning/testing counters are
preserved unchanged, following the upstream counter convention. Protocol differences
are documented in [BASELINES.md](../../benchmarks/BASELINES.md).

All common options can follow the image name directly, including `--seed`,
`--repetitions`, `--rule2`, `--rule3`, `--output-dir`, and Rust-specific settings.
`docker run --rm lsharp --help` lists them. For upstream-only options:

```bash
docker run --rm -v "$(pwd)/results:/results" lsharp --native --help
```

Native mode uses `/results/lsharp/native` as its isolated working directory.
It accepts the original CLI without translating flags; its raw CSV schema differs
from the common runner, and repetition/attempt handling is then the caller's job.


## Checks

```bash
./gradlew installDist
python3 scripts/test_rust_lsharp.py --classpath 'build/install/nclsharp/lib/*'
python3 scripts/test_benchmark_resume.py --classpath 'build/install/nclsharp/lib/*'
```

The adapter fixture checks raw-count preservation, input vs hypothesis size,
unsigned seeds, isolated working directories, resume, and malformed-output errors.
The real Docker smoke run verifies building/running the original upstream binary.
