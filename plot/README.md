# Plot benchmark CSVs

## Docker

From the repository root, after the three smoke runs in the main README finish,
copy their output CSVs here (copy only the files you want to plot):

```bash
cp results/smoke-manual/main/clsharp_results.csv \
   results/smoke-manual/main/nclsharp_results.csv \
   results/smoke-manual/clstar/clstar-cached_results.csv \
   results/smoke-manual/clstar/clstar-no-cache_results.csv \
   results/smoke-manual/lsharp/lsharp-rust_results.csv plot/
```

Build once, then run (no host Python or package installation needed):

```bash
docker build -f Dockerfile.plots -t clsharp-plots .
docker run --rm -v "$(pwd)/plot:/data" clsharp-plots \
  --clsharp clsharp_results.csv \
  --nclsharp nclsharp_results.csv \
  --clstar-cache clstar-cached_results.csv \
  --clstar-no-cache clstar-no-cache_results.csv \
  --lsharp lsharp-rust_results.csv \
  --expected-repeats 3 --min-size 1 \
  --output-dir figures/smoke-manual
```

This example writes PNGs and the statistics report to `plot/figures/smoke-manual/`.
Omit `--output-dir` to use `plot/figures/`. Use relative output paths to keep them
inside the mounted folder. For ten-repeat paper data, omit `--expected-repeats 3`
and `--min-size 1`. Input filenames and algorithm flags are unchanged; all algorithm
flags are optional. CSVs and generated figures are not baked into the image.

`docker run --rm clsharp-plots --help` lists options. Once built, the image runs
offline. Direct Python usage remains available below.

## Python

Use Python 3.10 or later. From this folder:

```bash
python3 -m venv .venv
. .venv/bin/activate
python -m pip install -r requirements.txt
```

Copy the algorithm output CSVs into this folder, then pass their filenames:

```bash
python plot_results.py \
  --lsharp lsharp-rust_results.csv \
  --clsharp clsharp_results.csv \
  --nclsharp nclsharp_results.csv \
  --clstar-cache clstar-cached_results.csv \
  --clstar-no-cache clstar-no-cache_results.csv
```

All five algorithm options are optional: supply only the series you want.
No arguments prints help. Paths are relative to the current directory; absolute
paths work too. CSV names can be changed freely. The source CSVs are never modified.

## Outputs and options

PNG figures and `plot_report.json` are written to `plot/figures/` by default.
Use `--output-dir PATH` to choose another folder. There is no PDF output.

- `figure5_learning.png`: learning symbols and resets, two panels.
- `figure6_testing.png`: testing symbols and resets; CL* is excluded as in the paper.
- `learning_symbols.png`, `learning_resets.png`, `testing_symbols.png`,
  `testing_resets.png`: individual panels (`--no-individual` disables these).
- `plot_report.json`: input hashes, sample counts, per-model statistics, fits,
  exclusions, clipped error bars, and package versions.

Useful options: `--errorbars sd|sem|ci95|none`, `--expected-repeats 10`,
`--models ../benchmarks/paper-models.txt`, `--fit power-law|semilog|none`,
`--xscale log|linear`, `--yscale log|linear|symlog`, `--dpi 300`.
Run `python plot_results.py --help` for all options.
The `../benchmarks/...` manifest example assumes direct Python execution from
`plot/`. Docker mounts only `plot/` in the commands above: copy a manifest there
and pass `--models paper-models.txt`, or mount its host folder separately.

## Data and statistics

The input header must be exactly:

```text
file,size,learnSymbols,learnResets,TestSymbols,TestResets
```

Each row is one successful repetition. Each point is the arithmetic mean for one
model; models with the same state count remain separate. Default bars are sample
SD (`ddof=1`). SEM is SD/sqrt(n); `ci95` is the two-sided Student-t interval with
`df=n-1`. Costs are used as exported.

By default each model needs exactly 10 repetitions. Incomplete groups are excluded
and reported; extra rows are rejected to catch mixed runs. Use `--expected-repeats N`
for another protocol. `--allow-partial` enables an explicitly labeled preview;
one-trial points are hollow and have no error bar because their spread is undefined.

The supplied non-L# series use their common model cohort. L# is restricted to its
available shared subset, at most 300 states by default, without shrinking all other
series to that range. Overall default range is 100–3000 states. The size limits
are configurable. CL* alone produces learning figures only.

Colors follow the paper: CL# blue, Naive CL# red, cached CL* gold, uncached CL*
black, L# green. Default axes are log-log; the trend is equal-weight least squares
on log10(mean) versus log10(size), requiring three distinct sizes. Fits stay within
the observed range. Nonpositive error-bar lower bounds are clipped on log axes;
original bounds remain in the report. Zero means require a linear or symlog axis
and `--fit none`.

The original plotting code is unavailable. These statistical choices are explicit;
identical historical error bars or regression formulas are not assumed.
