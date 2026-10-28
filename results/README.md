# Benchmark outputs

All benchmark outputs are kept here:

| Directory | Runs |
| --- | --- |
| `main/` | NCLSharp and CLSharp |
| `clstar/` | CL* with and without caching |
| `lsharp/` | Original Rust L# |
| `smoke/` | Short execution checks |

Each run writes `<algorithm>_results.csv`, `attempts.csv`, per-attempt logs and
raw counters, `run.properties`, `models.txt`, and `inputs.sha256`.
A `complete=true` flag indicates all requested successful repetitions finished.
Failures/timeouts remain in the attempt ledger; only successes enter summary CSVs.

Use a fresh directory for a new run. `--resume-from OLD --output-dir NEW` imports
finished trials and continues incomplete groups. Keep the same models, seed, rules,
cache, and oracle settings. It does not resume an individual interrupted trial.

The previous `build/benchmark-full-10`, `build/clstar-full-10` and
`build/lsharp-rust-smoke` local paths are compatibility symlinks. Existing CSV
contents and trial records were preserved; historical `test_results.csv` is unchanged.
Generated outputs are ignored by Git.

To make figures, manually copy the desired CSVs to [`plot/`](../plot/README.md)
and run the Python script there with their filenames.
