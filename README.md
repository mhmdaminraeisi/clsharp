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

From the repository root, **only Docker is needed for experiments**. Each image
builds independently and installs its own dependencies. Pinned submodule sources are prepared automatically; L# includes a checksum-checked
build-source snapshot for plain clones. No manual submodule command is required. The first build needs internet access to registries, Git hosts and
package repositories; completed images run offline.

```bash
docker build -t clsharp .
docker build -f Dockerfile.clstar -t clstar .
docker build -f Dockerfile.lsharp -t lsharp .
mkdir -p results

docker run --rm --memory=2g --cpus=2 -v "$(pwd)/results:/results" clsharp
docker run --rm --memory=5g --cpus=2 -v "$(pwd)/results:/results" clstar --heap-mb 4096
docker run --rm --memory=4g --cpus=2 -v "$(pwd)/results:/results" lsharp
```

| Image | Default algorithms | Models | Host output |
| --- | --- | --- | --- |
| `clsharp` | NCLSharp, CLSharp | `benchmarks/paper-models.txt` (114) | `results/main/` |
| `clstar` | CL* cached, uncached | `benchmarks/paper-models.txt` (114) | `results/clstar/` |
| `lsharp` | original Rust L# | `benchmarks/lsharp-models.txt` (30; 100–300 states) | `results/lsharp/` |

Default: 10 successful repetitions, seed 2027, ADS rules, up to 20 attempts per
model/algorithm, 1200 seconds per attempt. A first positional argument replaces the
model list; otherwise pass options directly after the image name:

```bash
docker run --rm -v "$(pwd)/results:/results" clsharp benchmarks/smoke.txt \
  --algorithms clsharp --repetitions 3 --seed 42 --rule2 SepSeq --rule3 Ads \
  --cache true --output-dir /results/smoke/main

docker run --rm clsharp --help
```

All images use the same option interface. `--rule2` / `--rule3` apply to main
learners and Rust L#; CL* caching is selected via `--algorithms clstar-cached` or
`clstar-no-cache`. Rust options include `--rust-extra-states`, `--rust-random-length`,
`--rust-cache-rules` and `--rust-threads`. `lsharp --native --help` exposes upstream
CLI options directly; use the common runner for repeated, normalized CSV output.
See [L#](baseline/lsharp-support/README.md) and [CL*](baseline/clstar/README.md).

Each run writes `<algorithm>_results.csv`, all attempt logs/statuses, configuration,
seeds, model hashes and raw counters. CSV header:
`file,size,learnSymbols,learnResets,TestSymbols,TestResets`.
Existing runs are protected: use a fresh `--output-dir`, or import an interrupted
run with `--resume-from /results/OLD --output-dir /results/NEW`, keeping the same
model manifest and protocol. [Output details](results/README.md).

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
