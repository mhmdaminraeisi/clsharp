# NCLSharp and CLSharp

Java implementations of NCLSharp and CLSharp, with an optional local L# learner.
For paper baselines, prefer the original [L# project](https://gitlab.science.ru.nl/sws/lsharp)
and the [external baseline instructions](benchmarks/BASELINES.md).

## Run with Docker

With Docker running, build and run the project from the repository root:

```bash
docker build -t nclsharp . && docker run --rm nclsharp
```

The image builds Java 21 dependencies and the pinned Hybrid-ADS revision automatically.
You do not need Java, Gradle, CMake, `init.sh`, or an initialized submodule on the host.
The first build needs internet access to download the build dependencies.

By default, this runs the current `Main` configuration: NCLSharp on
`src/main/resources/all_FSMs/49.dot`, using Hybrid-ADS for the full hypothesis
and random words for component testing. Results are printed to the terminal.
The command-line model and oracle options described below are not currently
parsed by `Main`.

To run the full benchmark and retrieve its CSV after it finishes:

```bash
docker run --name nclsharp-benchmark --memory=2g --cpus=2 nclsharp institute.teias.Benchmark benchmarks/models.txt
docker cp nclsharp-benchmark:/opt/nclsharp/build/benchmark-results/nclsharp_results.csv ./nclsharp_results.csv
docker cp nclsharp-benchmark:/opt/nclsharp/build/benchmark-results/clsharp_results.csv ./clsharp_results.csv
docker rm nclsharp-benchmark
```

The benchmark defaults to NCLSharp and CLSharp with Hybrid-ADS and ten
successful repetitions per model. Each algorithm writes a separate six-column CSV:
`file,size,learnSymbols,learnResets,TestSymbols,TestResets`.
Learning costs exclude counterexample minimization, exactly once:
`learnSymbols = raw learnInputs - minCeInputs` and
`learnResets = raw learnResets - minCeResets`. Testing counters are unchanged.
The historical `test_results.csv` is never overwritten.

## Requirements for running without Docker
Ensure your machine supports Java 21 or higher. You can verify the installation by executing the following command:

```bash
java -version
```

Also ensure that the `make` and `cmake` commands are installed. Run the following commands to verify this:
```bash
make -version
cmake -version
```

## Initialize
We used [this](https://github.com/Jaxan/hybrid-ads) submodule to use test Oracle `hybrid-ads`.
To load it, just run the following command:

```bash
chmod +x ./init.sh && ./init.sh
```

## Single-model example

`./gradlew run` runs the hardcoded example in `Main`. It currently does not
parse command-line options. For configurable experiments, use Benchmark below;
a one-line model list is sufficient for a single model.

## Create Benchmarks

Pass a model-list path as the first benchmark argument. Each nonempty line contains
a `.dot` filename from `src/main/resources/all_FSMs`; blank lines and lines starting
with `#` are ignored. Models run in the listed order. Empty lists, duplicate names,
and missing models are rejected before output files are opened.

`benchmarks/models.txt` contains the 178 models that have results for both
algorithms in the historical CSV and still exist in the repository. The five-model
`benchmarks/smoke.txt` is a quick check using models with fewer than 100 states.

Run the quick check from the repository root (five models, two algorithms,
three repetitions = 30 successful runs):

```bash
./gradlew runBenchmark --args="benchmarks/smoke.txt --output-dir build/benchmark-smoke --repetitions 3 --seed 2027"
python3 scripts/check_results.py build/benchmark-smoke --baseline test_results.csv > build/benchmark-smoke/comparison.json
```

Run the full shared-model list (178 models, ten repetitions per algorithm):

```bash
./gradlew runBenchmark --args="benchmarks/models.txt --output-dir build/benchmark-results --repetitions 10 --seed 2027"
```

Use a fresh output directory for each experiment; previous runs are not overwritten.
Default output: `build/benchmark-results`. The directory contains:

- `nclsharp_results.csv`, `clsharp_results.csv`: successful runs, six columns each.
- `attempts.csv`: every attempt, its algorithm, repetition, two seeds, status and elapsed time.
- `attempts/*.properties`: raw learning/testing/minCe counters, size, rounds and components for completed worker attempts. Timeouts and crashes may have no raw-counter file.
- `attempts/*.log`: per-attempt stdout/stderr, including stack traces on errors.
- `run.properties`: configuration, environment, revisions, timestamps and completion flag.
- `models.txt`: the actual ordered input list used for this run.
- `inputs.sha256`: hashes of model inputs and available source/build files.

Use `./gradlew runBenchmark --args="--help"` for options. Defaults: ten
successful repetitions, at most 20 attempts per model/algorithm, 1200 seconds
per attempt, 1024 MiB Java heap, ADS rules 2/3 and caching enabled.
Examples: `--algorithms nclsharp,clsharp,lsharp --rule2 SepSeq --rule3 Ads --cache false`.
CL* uses isolated LearnLib 0.16 workers: select `--algorithms clstar-cached,clstar-no-cache`.
See [CL* build and accounting instructions](baseline/clstar/README.md).
The oracle is Hybrid-ADS for full and partial testing. LSharp is optional; prefer
its [original implementation for paper baselines](benchmarks/BASELINES.md).

Each attempt runs in a separate JVM. Timeouts terminate that worker and its native
oracle; errors and failed learning attempts are recorded and retried. Any missing
successful repetitions cause a nonzero final exit status. Failed attempts do not
appear as successful CSV rows. A parent interruption can leave an unfinished run;
only `complete=true` in `run.properties` marks a completed experiment.

`--heap-mb` limits the worker Java heap, not native memory. For a total memory/CPU
limit, use Docker's `--memory` and `--cpus` options as below. The same model and
attempt receive the same seed pair across algorithms, independently of list order.
Fixed Hybrid-ADS seeds do not guarantee identical results: the learners still use
unordered collections. Compare distributions across repeated runs.

To run the quick check in Docker and save all outputs on the host:

```bash
mkdir -p build/benchmark-smoke-docker
docker run --rm --memory=2g --cpus=2 -v "$(pwd)/build/benchmark-smoke-docker:/results" nclsharp institute.teias.Benchmark benchmarks/smoke.txt --output-dir /results --repetitions 3 --seed 2027
```

Rebuild the image after changing code or model lists. The full shared-model list
includes models below 100 states; it is not a paper-specific selection. Historical
data are a reference for trends, not exact expected output; original seeds and raw
paper plotting data are unavailable. `scripts/check_results.py` verifies that CSV
costs match raw counters and reports per-model mean ratios against historical data.
The plotting script and final artifact freeze will be added later.

To inspect an ongoing detached full run:

```bash
docker logs -f nclsharp-full
```

After it exits, confirm its exit status and validate the saved results:

```bash
docker inspect --format '{{.State.Status}} {{.State.ExitCode}}' nclsharp-full
python3 scripts/check_results.py build/benchmark-full --baseline test_results.csv > build/benchmark-full/comparison.json
```

A successful complete default run has 1780 rows per algorithm (plus CSV headers),
3560 successful attempts in total, and `complete=true`. Retry attempts can make the
attempt count larger. Always inspect failures as well as successful-run averages;
filtering to successful learning runs can affect comparisons.

## Resume with fewer repetitions

`--repetitions` is configurable; its default remains 10. Stop the previous run
before importing it. Resume always writes a **new** directory and leaves the old
one unchanged. Keep the same model list, algorithms, seed, cache and rule settings.

```bash
./gradlew runBenchmark --args="benchmarks/models.txt --repetitions 3 --seed 2027 --resume-from build/benchmark-full --output-dir build/benchmark-full-3"
```

Existing successful trials are reused in recorded order. Only the first three per
model/algorithm contribute to the new CSVs. Additional successes remain in the
attempt log as `surplus_success`, with their raw counters and logs preserved.
Incomplete groups continue with the next attempt number and corresponding seeds;
the total attempt cap still applies. Model hashes and configuration are checked
before importing. `imported-run.properties` and `imported-inputs.sha256` retain the
old provenance, while the new metadata records the current executable/source hashes.
An interrupted attempt with no ledger entry is not considered a completed trial.

In Docker, mount the old run read-only at `/previous` and the fresh output at `/results`:

```bash
mkdir -p build/benchmark-full-3
docker run --name nclsharp-full-3 --memory=2g --cpus=2 -v "$(pwd)/build/benchmark-full:/previous:ro" -v "$(pwd)/build/benchmark-full-3:/results" nclsharp institute.teias.Benchmark benchmarks/models.txt --repetitions 3 --seed 2027 --resume-from /previous --output-dir /results
```

For a three-repeat default run, the target is **534 successful CSV rows per
algorithm / 1068 total**. Imported surplus successes are not counted toward that
target. Use fresh output/container names when re-running these examples.
