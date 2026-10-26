# External baselines

The L# baseline uses its original project. The supplied modified CL* implementation
is now integrated under [`baseline/clstar`](../baseline/clstar/README.md), with
cached and no-cache modes and the root Hybrid-ADS executable. Historical raw paper
data are unavailable; these instructions define new reproducible executions.
The local optional `--algorithms lsharp` is a Java implementation and does not
substitute for the original Rust baseline.

## L#

Original source: <https://gitlab.science.ru.nl/sws/lsharp>.
Use the archived TACAS 2022 artifact, version 0.1.0:
<https://doi.org/10.5281/zenodo.5735533>.
Its `artifact.zip` has upstream MD5 `852abbb3f50b24d436d42c4a116f222c`.
Follow its [setup instructions](https://zenodo.org/records/5735533/files/README.txt).
In its prepared environment, run:

```bash
cd ~/docker_sub/automata-lib
chmod +x run_me.sh
./run_me.sh 10
```

This runs the upstream suite. For this project's models, adapt the upstream model
selection to `models.txt` and preserve the input hashes, command, oracle settings
and outputs separately. The archive predates this project's renamed learners.

## CL*

Original project: <https://github.com/faezeh-lbf/CL-Star>.
The imported checkout was at `899d16834f4bc96fc997d3873aecbab22577a020` with local
modifications; [NOTICE.md](../baseline/clstar/NOTICE.md) records provenance and
source hashes. The original random-words command is not the modified HADS baseline.

Run both variants through the common benchmark interface:

```bash
./gradlew runBenchmark --args="benchmarks/smoke.txt --algorithms clstar-cached,clstar-no-cache --repetitions 3 --seed 2027 --output-dir build/clstar-smoke"
```

For the full list, replace `benchmarks/smoke.txt` with `benchmarks/models.txt` and
choose a fresh output directory. The paper describes ten repetitions; use
`--repetitions 10` for that protocol. The three-repeat run is a shorter validation.

See the [CL* guide](../baseline/clstar/README.md) for building, Docker, cache
placement, per-EQ budgets, cost accounting, independent validation and tests.
Only learning costs are compared for CL* in the paper; CL* testing columns are
exported for diagnostics and are excluded from the Figure 6 comparison.

For new cross-project comparisons, preserve versions, model hashes, seeds, budgets,
cache configuration and raw counters. The main learners and this supplied CL*
protocol differ in their reference guards/termination policy. Shared Hybrid-ADS
code does not imply identical test budgets. Plotting integration remains separate.
