# NCLSharp and CLSharp

Compositional active learning of Mealy machines. **CLSharp (CL#)** is the main
algorithm; **NCLSharp (Naive CL#)** is its naive variant.

Tested on a MacBook Pro (Intel Core i7, 2.6 GHz, 6 cores, 16 GB RAM), macOS 15.1.1,
Docker Engine 27.3.1, Linux/amd64 containers. Docker had about 7.65 GiB RAM available.
Other architectures have not been validated.

## Structure

- `src/`: main Java algorithms, benchmark runner, and shared `.dot` models.
- `baseline/clstar/`: modified [CL*](https://github.com/faezeh-lbf/CL-Star), with
  Hybrid-ADS and cached/uncached modes. [Provenance](baseline/clstar/NOTICE.md).
- `baseline/lsharp/`: original Rust [L#](https://gitlab.science.ru.nl/sws/lsharp),
  pinned as a Git submodule; its adapter/build settings are in `baseline/lsharp-support/`.
- `hybrid-ads/`: pinned [Hybrid-ADS](https://github.com/Jaxan/hybrid-ads) Git submodule,
  a C++ test generator using adaptive distinguishing sequences for equivalence queries.
  Java learners and CL* share this generator; original L# uses its internal Rust HADS.
- `benchmarks/`: model manifests and [baseline protocols](benchmarks/BASELINES.md).
- `results/`: run outputs. `plot/`: standalone Python plotting script.

## Build and run

Run commands from the repository root. **Only Docker is needed for experiments**;
each image builds independently and installs its dependencies. Pinned submodule
sources are prepared automatically, including a checksum-checked L# source snapshot.
No manual submodule command is required. The first build needs internet access;
completed images run offline.

```bash
docker build -t clsharp .
docker build -f Dockerfile.clstar -t clstar .
docker build -f Dockerfile.lsharp -t lsharp .
mkdir -p results
```

| Image | Default algorithms | Models | Host output |
| --- | --- | --- | --- |
| `clsharp` | NCLSharp, CLSharp | `benchmarks/paper-models.txt` (114) | `results/main/` |
| `clstar` | CL* cached, uncached | `benchmarks/paper-models.txt` (114) | `results/clstar/` |
| `lsharp` | original Rust L# | `benchmarks/lsharp-models.txt` (30; 100–300 states) | `results/lsharp/` |

First try the five-model smoke list (4, 12, 32, 36 and 64 states), with three
successful repetitions per algorithm. Allow approximately **5 minutes** for the
three smoke commands, excluding image builds/downloads. Run them one at a time:

```bash
docker run --rm -v "$(pwd)/results:/results" clsharp benchmarks/smoke.txt \
  --repetitions 3 --seed 2027 --output-dir /results/smoke-manual/main
docker run --rm -v "$(pwd)/results:/results" clstar benchmarks/smoke.txt \
  --repetitions 3 --seed 2027 --output-dir /results/smoke-manual/clstar
docker run --rm -v "$(pwd)/results:/results" lsharp benchmarks/smoke.txt \
  --repetitions 3 --seed 2027 --output-dir /results/smoke-manual/lsharp
```

For full runs, omit the smoke manifest and options to use the table's defaults:

```bash
docker run --rm -v "$(pwd)/results:/results" clsharp
docker run --rm -v "$(pwd)/results:/results" clstar
docker run --rm -v "$(pwd)/results:/results" lsharp
```

For the two main learners, the historical estimate is **about 3 hours combined**:
NCLSharp about 58 minutes and CLSharp about 116 minutes, on the 114 paper models
with ten repetitions each. This estimate uses `test_results.csv` and excludes
image builds, worker startup and retry overhead; hardware and code changes affect
actual times. CL* and original Rust L# are not included in that total.
[Calculation and timing details](results/README.md#expected-running-time).

## Options and defaults

The full-run commands above do not specify CPU, container RAM or Java heap limits.
The following three Bash examples show optional configuration for each family.
They use the paper manifests and fresh output folders; choose the smoke manifest
and `--repetitions 3` for shorter checks.

**CLSharp and NCLSharp:**

```bash
docker run --rm \
  --memory=4g --cpus=2 \
  -v "$(pwd)/results:/results" \
  clsharp benchmarks/paper-models.txt \
  --algorithms nclsharp,clsharp \
  --output-dir /results/configured-main \
  --resume-from "" \
  --repetitions 10 \
  --max-attempts 20 \
  --seed 2027 \
  --timeout-seconds 1200 \
  --heap-mb 2048 \
  --rule2 Ads \
  --rule3 Ads \
  --cache true
```

**CL* with and without caching:**

```bash
docker run --rm \
  --memory=4g --cpus=2 \
  -v "$(pwd)/results:/results" \
  clstar benchmarks/paper-models.txt \
  --algorithms clstar-cached,clstar-no-cache \
  --output-dir /results/configured-clstar \
  --resume-from "" \
  --repetitions 10 \
  --max-attempts 20 \
  --seed 2027 \
  --timeout-seconds 1200 \
  --heap-mb 2048 \
  --clstar-classpath '/opt/nclsharp/clstar/lib/*' \
  --hads-max-tests 10000 \
  --hads-timeout-seconds 1200
```

**Original Rust L#:**

```bash
docker run --rm \
  --memory=4g --cpus=2 \
  -v "$(pwd)/results:/results" \
  lsharp benchmarks/lsharp-models.txt \
  --algorithms lsharp-rust \
  --output-dir /results/configured-lsharp \
  --resume-from "" \
  --repetitions 10 \
  --max-attempts 20 \
  --seed 2027 \
  --timeout-seconds 1200 \
  --rule2 Ads \
  --rule3 Ads \
  --rust-lsharp-executable /opt/nclsharp/lsharp/lsharp \
  --rust-extra-states 10 \
  --rust-random-length 10 \
  --rust-cache-rules false \
  --rust-threads 2
```

Docker options go **before** the image name; benchmark options go **after** it.
The values above are examples; the defaults below apply when an option is omitted.
`--resume-from ""` explicitly starts fresh in Bash; normally omit that option for a
new run, or supply a compatible previous run's container path to resume.

| Docker option | Meaning | Default when omitted |
| --- | --- | --- |
| `--rm` | Remove the container after it exits; mounted outputs remain | Keep the stopped container |
| `--memory SIZE` | Container RAM cap, e.g. `4g` | No explicit container cap |
| `--cpus N` | Container CPU quota, e.g. `2` | No explicit container quota |
| `-v HOST:CONTAINER` | Mount host files; the example persists outputs in `results/` | No host-folder mount |

| Benchmark argument | Meaning | Default in Docker |
| --- | --- | --- |
| First positional argument | Model manifest: one existing `.dot` filename per line | Per-image manifest in the table above |
| `--algorithms LIST` | Comma-separated learner names for the selected image | Per-image algorithms in the table above |
| `--output-dir DIR` | Fresh output directory, using a container path | `/results/main`, `/results/clstar`, or `/results/lsharp`, by image |
| `--resume-from DIR` | Import finished attempts from a compatible previous run | Empty: start fresh |
| `--repetitions N` | Successful runs per model/algorithm | `10` |
| `--max-attempts N` | Total attempt cap per model/algorithm, including imported attempts | `20`; must be at least repetitions |
| `--seed N` | Master seed used to derive individual trial seeds | `2027` |
| `--timeout-seconds N` | Hard timeout for an entire attempt | `1200` seconds |
| `--heap-mb N` | Maximum Java worker heap; not a Rust memory limit | `1024` MiB |
| `--rule2 Nothing\|SepSeq\|Ads` | Rule 2 strategy for main learners and Rust L# | `Ads` |
| `--rule3 SepSeq\|Ads` | Rule 3 strategy for main learners and Rust L# | `Ads` |
| `--cache true\|false` | Cache for the main Java learners; CL* mode is selected by its algorithm name | `true` |
| `--clstar-classpath CP` | Separate LearnLib 0.16 classpath for CL* | `/opt/nclsharp/clstar/lib/*` |
| `--hads-max-tests N` | CL* test budget per equivalence-query call | `10000` |
| `--hads-timeout-seconds N` | CL* timeout per equivalence-query call | `1200` seconds |
| `--rust-lsharp-executable PATH` | Original Rust L# executable, available in the `lsharp` image | `/opt/nclsharp/lsharp/lsharp` |
| `--rust-extra-states N` | Rust HADS extra-state parameter (`k`) | `10` |
| `--rust-random-length N` | Rust HADS expected random infix length | `10` |
| `--rust-cache-rules true\|false` | Original Rust rule cache | `false` |
| `--rust-threads N` | Rust Rayon thread count | `2` |

Omitting `--memory` and `--cpus` allows use of the resources available to Docker;
on macOS/Windows, Docker Desktop's own allocation still applies. Omitting `--heap-mb`
keeps the Java worker default above. Trials run sequentially; removing CPU limits
does not make them parallel. Only options for the selected algorithms affect learning.

Each attempt derives its seeds from the master seed, model name and attempt number.
Corresponding attempts across algorithms receive the same seed pair; actual seeds
are recorded in `attempts.csv`. The optional local Java L# uses `--algorithms lsharp`;
original Rust L# uses `lsharp-rust` in the `lsharp` image.

Model manifests ignore blank lines and lines starting with `#`. To use a host-edited
manifest without rebuilding, add `-v "$(pwd)/benchmarks:/manifests:ro"` before the image
name and pass `/manifests/my-models.txt` after it.
Run `docker run --rm clsharp --help`; replace the image name for other images' help.
`docker run --rm lsharp --native --help` exposes the original Rust CLI separately.
See [L#](baseline/lsharp-support/README.md) and [CL*](baseline/clstar/README.md).

Each run writes `<algorithm>_results.csv`, attempt logs/statuses, configuration,
seeds, model hashes and raw counters. CSV header:
`file,size,learnSymbols,learnResets,TestSymbols,TestResets`.
Existing runs are protected: choose a fresh `--output-dir`, e.g. `/results/smoke-manual-2/main`,
or use `--resume-from /results/OLD --output-dir /results/NEW` with the same model
manifest and protocol. [Completion checks, outputs and resume](results/README.md).

## Plot results

Copy the desired output CSVs into `plot/`. Build once and run from the repository
root; Python and plotting dependencies are installed inside the image:

```bash
docker build -f Dockerfile.plots -t clsharp-plots .
docker run --rm -v "$(pwd)/plot:/data" clsharp-plots \
  --clsharp clsharp_results.csv --nclsharp nclsharp_results.csv \
  --clstar-cache clstar-cached_results.csv --clstar-no-cache clstar-no-cache_results.csv \
  --lsharp lsharp-rust_results.csv
```

Every algorithm argument is optional. PNG figures and statistics go to
`plot/figures/`. For three-repeat smoke data, add `--expected-repeats 3 --min-size 1`
and optionally `--output-dir figures/smoke-manual`. Default bars are sample SD;
`--errorbars sem` or `ci95` changes this. CL* appears only in learning figures.
[Plot options, CSV copying, and direct Python usage](plot/README.md).
