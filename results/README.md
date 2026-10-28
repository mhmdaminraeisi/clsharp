# Benchmark outputs

All benchmark outputs are kept here:

| Directory | Runs |
| --- | --- |
| `main/` | NCLSharp and CLSharp |
| `clstar/` | CL* with and without caching |
| `lsharp/` | Original Rust L# |
| `smoke/` | Earlier short execution checks |
| `smoke-manual/{main,clstar,lsharp}/` | The README's three-repeat smoke runs |

Each run writes `<algorithm>_results.csv`, `attempts.csv`, per-attempt logs and
raw counters, `run.properties`, `models.txt`, and `inputs.sha256`.
A `complete=true` flag indicates all requested successful repetitions finished.
Failures/timeouts remain in the attempt ledger; only successes enter summary CSVs.

## Expected running time

Allow approximately **5 minutes** for the five-model, three-repeat smoke sequence
across the three images, after image builds and downloads have completed.

The full-run estimate below covers only NCLSharp and CLSharp on the **114 models**
in [`benchmarks/paper-models.txt`](../benchmarks/paper-models.txt), with **10 successful
repetitions each**, executed sequentially (2,280 successful trials in total).
The source is the successful `nclsharp-ADS` / `clsharp-ADS` rows in
[`test_results.csv`](../test_results.csv). Every model/algorithm pair has three
historical observations. `RunTime` is in milliseconds, as recorded by the historical
Java runner using `System.currentTimeMillis()`.

For each model and algorithm, average its recorded `RunTime`, multiply by 10,
then sum over the selected models and algorithms:

```text
estimated milliseconds = sum_model sum_algorithm (mean(RunTime) * 10)
```

| Algorithm | Estimated milliseconds, 10 repetitions per model | Approximate duration |
| --- | ---: | ---: |
| NCLSharp | 3,477,940 | 57.97 minutes |
| CLSharp | 6,981,570 | 116.36 minutes |
| Combined | 10,459,510 | 174.33 minutes (about 2 h 54 min; allow about 3 h) |

This is a historical extrapolation. It excludes image builds/downloads, worker
startup, result-file I/O and retry overhead. Actual times depend on hardware,
resource settings and the current implementation. It does not estimate CL* or
original Rust L# full-run times.

## Check completion

After the README's three smoke commands finish, each of the five summary CSVs
should contain 15 results plus one header (five models × three successes):

```bash
wc -l results/smoke-manual/main/*_results.csv \
      results/smoke-manual/clstar/*_results.csv \
      results/smoke-manual/lsharp/*_results.csv
grep '^complete=' results/smoke-manual/main/run.properties \
                  results/smoke-manual/clstar/run.properties \
                  results/smoke-manual/lsharp/run.properties
```

Each CSV should have 16 lines and all three run flags should be `complete=true`.
On incomplete completion the runner exits nonzero; inspect `attempts.csv` and its
linked logs. Three repetitions means three successes; retries can add more attempts.

Use a fresh directory for a new run. `--resume-from OLD --output-dir NEW` imports
finished trials and continues incomplete groups. Keep the same models, seed, rules,
cache, and oracle settings. It does not resume an individual interrupted trial.
If increasing the target above 20, also raise `--max-attempts` to at least the
requested repetition count; the cap includes imported attempts.

The previous `build/benchmark-full-10`, `build/clstar-full-10` and
`build/lsharp-rust-smoke` local paths are compatibility symlinks. Existing CSV
contents and trial records were preserved; historical `test_results.csv` is unchanged.
Generated outputs are ignored by Git.

To make figures, manually copy the desired CSVs to [`plot/`](../plot/README.md)
and follow its Docker or direct Python instructions with their filenames. The
Docker plotting image installs its own Python dependencies.
