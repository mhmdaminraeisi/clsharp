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
