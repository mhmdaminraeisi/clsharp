# External baselines

The paper's L# baseline was run using its original project. The CL* baseline
used a modified external implementation connected to Hybrid-ADS. That adapter
and its build/run integration are not included yet and are deferred to the final
artifact preparation. The upstream CL* command below is a reference, not a
reproduction of that modified baseline.

This repository's optional `--algorithms lsharp` runs the local Java implementation;
it does not reproduce those original baseline executions. The exact historical
baseline revision, configuration and raw paper data are unavailable here. The
versions below are reproducible reference versions, not claimed historical ones.

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

Source and instructions: <https://github.com/faezeh-lbf/CL-Star>.
Reference revision checked on 2026-10-24:
`ae09edf9d2f4d29df45cd3d541c83dce2b68816b`.

```bash
git clone https://github.com/faezeh-lbf/CL-Star.git
cd CL-Star
git checkout --detach ae09edf9d2f4d29df45cd3d541c83dce2b68816b
java -cp 'libs/*:cl-star.jar' Run_experiment -src_dir data/experiment_benchmarks.txt -repeat 10 -eq rndWords
```

Supply the intended models through the upstream list format and configure
`benchmarks_base_dir` and `result_path` in its `.experimentProps`.
The upstream default oracle is random words; this command does not assert oracle
parity with this repository's Hybrid-ADS. Upstream output is `data/Results.csv`
unless configured otherwise. See its README and `documentation.txt` for Docker
setup and available equivalence oracles.

For any new cross-project comparison, record versions, model hashes, repetitions,
seeds (where supported), oracle budgets, caching and counter definitions. Confirm
counterexample-minimization accounting before combining outputs; do not subtract
this project's minCe from unrelated baseline counters automatically.
