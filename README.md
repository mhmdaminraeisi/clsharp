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
successful repetitions per algorithm. Run these commands one at a time:

```bash
docker run --rm --memory=2g --cpus=2 -v "$(pwd)/results:/results" clsharp benchmarks/smoke.txt \
  --repetitions 3 --seed 2027 --output-dir /results/smoke-manual/main
docker run --rm --memory=2g --cpus=2 -v "$(pwd)/results:/results" clstar benchmarks/smoke.txt \
  --repetitions 3 --seed 2027 --output-dir /results/smoke-manual/clstar
docker run --rm --memory=4g --cpus=2 -v "$(pwd)/results:/results" lsharp benchmarks/smoke.txt \
  --repetitions 3 --seed 2027 --output-dir /results/smoke-manual/lsharp
```

For full runs, omit the smoke manifest and options to use the table's defaults:

```bash
docker run --rm --memory=2g --cpus=2 -v "$(pwd)/results:/results" clsharp
docker run --rm --memory=5g --cpus=2 -v "$(pwd)/results:/results" clstar --heap-mb 4096
docker run --rm --memory=4g --cpus=2 -v "$(pwd)/results:/results" lsharp
```

Default: 10 successful repetitions, seed 2027, ADS rules, up to 20 attempts per
model/algorithm, 1200 seconds per attempt. A first positional argument replaces the
model list; options follow the image name (or the manifest). Use `--algorithms clsharp`
to run only CLSharp, or `--rule2 SepSeq --rule3 Ads --cache true` to change its settings.
Run `docker run --rm clsharp --help`; replace the image name for other images' help.

Model manifests contain one existing `.dot` filename per line; blank lines and
lines starting with `#` are ignored. To use a host-edited manifest without rebuilding, add
`-v "$(pwd)/benchmarks:/manifests:ro"` and pass `/manifests/my-models.txt`.

`--memory` and `--cpus` are optional container limits. Without them, resources are
still bounded by Docker Desktop's allocation on macOS (Settings → Resources).
Java workers retain their own default 1024 MiB heap; change it with `--heap-mb N`.
Rust uses `--rust-threads 2` by default. The runner processes trials sequentially;
removing CPU limits does not make them parallel.

`--rule2` / `--rule3` apply to main learners and Rust L#; CL* caching is selected
via `--algorithms clstar-cached` or `clstar-no-cache`. Rust options include
`--rust-extra-states`, `--rust-random-length`, `--rust-cache-rules` and `--rust-threads`.
`docker run --rm lsharp --native --help` exposes the original Rust CLI.
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
