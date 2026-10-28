# CL* with the shared Hybrid-ADS executable

This folder contains the CL* algorithm and adapters, without a private model suite
or Hybrid-ADS checkout. Attribution and import provenance are in [NOTICE.md](NOTICE.md).
Run all commands from the repository root. Java 21 and the root native executable
`hybrid-ads/build/main` are required; use the root `init.sh` or Docker to build it.

## Build and run

CL* uses **LearnLib 0.16.0** in a separate Gradle source set/JVM. The main CLSharp,
NCLSharp and LSharp code continues to use 0.17.0. Their classpaths are not merged.
Build with `./gradlew installClstar`; `runBenchmark` also builds it automatically.

Five-model check of both cache modes:

```bash
./gradlew runBenchmark --args="benchmarks/smoke.txt --algorithms clstar-cached,clstar-no-cache --repetitions 3 --seed 2027 --output-dir results/smoke/clstar"
python3 scripts/check_results.py results/smoke/clstar > results/smoke/clstar/validation.json
```

Full shared-model list (ten repetitions, as described in the paper):

```bash
./gradlew runBenchmark --args="benchmarks/paper-models.txt --algorithms clstar-cached,clstar-no-cache --repetitions 10 --seed 2027 --output-dir results/clstar"
```

Each run needs a fresh output directory. Use `--repetitions 3` for a shorter
validation run. Output files are `clstar-cached_results.csv` and
`clstar-no-cache_results.csv`, plus the same raw counters, logs, metadata and model
hashes as the main benchmark. Failures, incorrect learned models and timeouts are
not exported as successful rows. `--help` lists retry, timeout and heap options.

The dedicated Docker image builds all dependencies and the shared native generator.
Optional resource settings are explained in the [parameter guide](../../README.md#options-and-defaults):

```bash
docker build -f Dockerfile.clstar -t clstar .
mkdir -p results/smoke/clstar-docker
docker run --rm -v "$(pwd)/results:/results" clstar benchmarks/smoke.txt --algorithms clstar-cached,clstar-no-cache --repetitions 3 --seed 2027 --output-dir /results/smoke/clstar-docker
```

## Oracle and cache semantics

Both modes use exactly the same CL* learner and global/component HADS adapters:
`-p buggy -m random -k 10 -s hads -r 10`. `-e` allows repeated generated words,
avoiding exhaustion of distinct words over small component alphabets. The shared
native revision is `f23946f00c1c111c9b061b6f43d1ffe8b32582bd`.

The supplied modified cached runner used a budget of 10,000 nonempty tests per EQ
call and no perfect-reference guard during learning. This integration preserves
that setting for **both** cache modes. Adjust `--hads-max-tests` and
`--hads-timeout-seconds` explicitly if needed; defaults are 10000 and 1200 seconds.
A completed budget without a counterexample returns no CE; an oracle timeout,
unexpected stream termination, unknown input or executable error fails the trial.
The outer `--timeout-seconds` bounds the entire trial, independently of per-call
oracle limits. A perfect oracle validates the final learned model only; its CE
never guides learning and validation costs are outside the learner counters.

- `clstar-cached`: separate LearnLib DAG caches for MQ and EQ, created fresh per
  trial. Global and component HADS share the EQ cache. Counters below the caches
  measure actual target executions after filtering.
- `clstar-no-cache`: both DAG caches are bypassed. Every target query executes.
- The common runner's `--cache` flag controls the main Java learners; CL* cache
  selection is determined by the explicit algorithm name above.
- Paired trials receive the same initial seed pair. Alphabet shuffling and each
  actual native generator seed are recorded/reproducible from the raw metadata.

The current main learners use a reference-equivalence guard and a different test
termination policy. Sharing the native executable alone does not establish equal
EQ budgets across implementations. These settings reproduce the supplied modified
baseline protocol; they do not assert exact reproduction of unavailable paper data.

## Accounting and the paper

Each summary CSV has `file,size,learnSymbols,learnResets,TestSymbols,TestResets`.
Per-attempt properties retain the full raw counters for independent validation.

As in the supplied modified runner, the terminal global EQ's actual symbol/reset
deltas are excluded from reported test costs, while all its tests still run.
`test*IncludingFinalEq` and `excludedFinalEq*` retain the full accounting. Component
terminal calls remain included. Wall-clock attempt time includes all execution and
validation; raw `learningNanosIncludingFinalEq` and `validationNanos` are explicit.

The paper evaluates cached and non-cached CL* for **learning** costs and excludes
CL* from the testing plot (Figure 6). The exported CL* testing columns are diagnostic;
do not automatically add them to that plot. The shared manifest includes small
models for validation, while the paper describes models with 100–3000 states.
A successful smoke run validates the integration, not every plotted paper claim.

## Checks

```bash
./gradlew checkClstar
./gradlew installDist
python3 scripts/test_benchmark_resume.py --classpath 'build/install/nclsharp/lib/*'
```

The oracle checks exercise real HADS generation, cached/uncached replay, a deliberate
counterexample and explicit generator failures. The resume checks exercise reducing
ten repeats to three without rerunning successful trials and reject changed seeds
or model bytes.

If Maven is unreachable, pass the checksum-pinned LearnLib bundle to Gradle via
`-PclstarDependencyJar=/absolute/path/to/learnlib-distribution-0.16.0-dependencies-bundle.jar`.
The project does not include precompiled dependency jars or a second native project.
