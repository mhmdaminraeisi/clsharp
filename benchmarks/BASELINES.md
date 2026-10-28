# External baselines

The L# baseline uses its original project. The supplied modified CL* implementation
is now integrated under [`baseline/clstar`](../baseline/clstar/README.md), with
cached and no-cache modes and the root Hybrid-ADS executable. Historical raw paper
data are unavailable; these instructions define new reproducible executions.
The local optional `--algorithms lsharp` is a Java implementation;
`--algorithms lsharp-rust` runs the original Rust baseline in its dedicated image.

## L# (original Rust implementation)

The canonical repository <https://gitlab.science.ru.nl/sws/lsharp> is registered
as the `baseline/lsharp` submodule at commit
`c5593e341c5796afd620a922900eade2ac8413c1`. Its tracked source is unchanged.
[`Dockerfile.lsharp`](../Dockerfile.lsharp) handles Rust/Cargo prerequisites and
uses the common benchmark adapter with the explicit name `lsharp-rust`.

[`benchmarks/lsharp-models.txt`](lsharp-models.txt) selects the 30 paper models
with size 100–300 inclusive (actual range: 112–288), preserving original names.
Build/run commands, pinned dependency resolution, oracle settings and counter
conventions are in the [original Rust baseline guide](../baseline/lsharp-support/README.md).
This uses upstream's internal `hads-int` oracle, not the C++ generator process.
Its raw counters are preserved, without an invented minCe subtraction.

The TACAS 2022 archive remains an additional reference:
<https://doi.org/10.5281/zenodo.5735533>, version 0.1.0.
Neither that archive nor the pinned reference checkout is asserted to be the exact
unavailable historical baseline configuration/raw data used for this paper.

## CL*

Original project: <https://github.com/faezeh-lbf/CL-Star>.
The imported checkout was at `899d16834f4bc96fc997d3873aecbab22577a020` with local
modifications; [NOTICE.md](../baseline/clstar/NOTICE.md) records provenance and
source hashes. The original random-words command is not the modified HADS baseline.

Run both variants through the common benchmark interface:

```bash
docker build -f Dockerfile.clstar -t clstar .
docker run --rm -v "$(pwd)/results:/results" clstar benchmarks/smoke.txt --repetitions 3 --seed 2027 --output-dir /results/smoke/clstar
```

For the full list, replace `benchmarks/smoke.txt` with `benchmarks/paper-models.txt` and
choose a fresh output directory. The paper describes ten repetitions; use
`--repetitions 10` for that protocol. The three-repeat run is a shorter validation.

See the [CL* guide](../baseline/clstar/README.md) for building, Docker, cache
placement, per-EQ budgets, cost accounting, independent validation and tests.
Only learning costs are compared for CL* in the paper; CL* testing columns are
exported for diagnostics and are excluded from the Figure 6 comparison.

For new cross-project comparisons, preserve versions, model hashes, seeds, budgets,
cache configuration and raw counters. The main learners and this supplied CL*
protocol differ in their reference guards/termination policy. Shared Hybrid-ADS
code does not imply identical test budgets. The standalone plotting workflow is documented in [plot/README.md](../plot/README.md).
