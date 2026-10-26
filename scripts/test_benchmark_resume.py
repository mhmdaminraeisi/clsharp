#!/usr/bin/env python3
"""Regression checks for importing ten-repeat results into a three-repeat run.
Run after installDist: python3 scripts/test_benchmark_resume.py --classpath 'build/install/nclsharp/lib/*'
"""
import argparse
import csv
import hashlib
import subprocess
import tempfile
from pathlib import Path
from check_results import check

HEADER = 'file,size,learnSymbols,learnResets,TestSymbols,TestResets\n'


def fixture(root):
    old = root / 'old'
    (old / 'attempts').mkdir(parents=True)
    (old / 'run.properties').write_text('algorithms=nclsharp,clsharp\nseed=2027\nrule2=Ads\nrule3=Ads\ncache=true\n'
        'oracle=hybrid-ads -p buggy -m random -k 10 -s hads -r 10\n'
        'hybrid-ads-revision=f23946f00c1c111c9b061b6f43d1ffe8b32582bd\nrepetitions=10\n')
    (old / 'models.txt').write_text('1.dot\n')
    model = Path('src/main/resources/all_FSMs/1.dot')
    (old / 'inputs.sha256').write_text(hashlib.sha256(model.read_bytes()).hexdigest() + '  ' + str(model) + '\n')
    attempts = ['file,algorithm,attempt,repetition,seed,partialSeed,status,elapsedMillis,detail']
    for alg in ('nclsharp', 'clsharp'):
        exported = HEADER
        for n in range(1, 11):
            name = f'1.dot-{alg}-{n}'
            (old / 'attempts' / (name + '.log')).write_text('original trial\n')
            (old / 'attempts' / (name + '.properties')).write_text(
                f'size=4\nlearned=true\nlearnInputs={100+n}\nlearnResets=30\nminCeInputs=10\nminCeResets=3\ntestInputs=50\ntestResets=5\n')
            attempts.append(f'1.dot,{alg},{n},{n},0,0,success,1,attempts/{name}.log')
            exported += f'1.dot,4,{90+n},27,50,5\n'
        (old / (alg + '_results.csv')).write_text(exported)
    (old / 'attempts.csv').write_text('\n'.join(attempts) + '\n')
    return old


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--classpath', required=True)
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix='clsharp-resume-test-') as tmp:
        root = Path(tmp)
        old = fixture(root)
        original = (old / 'attempts.csv').read_bytes()
        out = root / 'resumed'
        base = ['java', '-DLOG_LEVEL=WARN', '-cp', args.classpath, 'institute.teias.Benchmark',
                str(old / 'models.txt'), '--resume-from', str(old), '--repetitions', '3']
        subprocess.run(base + ['--output-dir', str(out)], check=True, capture_output=True, text=True, timeout=20)
        report = check(out, None)
        assert report['complete'] and report['statuses'] == {'success': 6, 'surplus_success': 14}
        for alg in ('nclsharp', 'clsharp'):
            data = list(csv.DictReader((out / (alg + '_results.csv')).open()))
            assert [int(r['learnSymbols']) for r in data] == [91, 92, 93]
        assert (old / 'attempts.csv').read_bytes() == original
        wrong_seed = subprocess.run(base + ['--output-dir', str(root / 'wrong-seed'), '--seed', '2028'], capture_output=True, text=True, timeout=20)
        assert wrong_seed.returncode != 0 and 'Resume configuration mismatch: seed' in wrong_seed.stderr
        (old / 'inputs.sha256').write_text('0' * 64 + '  src/main/resources/all_FSMs/1.dot\n')
        changed_model = subprocess.run(base + ['--output-dir', str(root / 'changed-model')], capture_output=True, text=True, timeout=20)
        assert changed_model.returncode != 0 and 'Changed resume model' in changed_model.stderr
        (old / 'inputs.sha256').write_text('')
        missing_hash = subprocess.run(base + ['--output-dir', str(root / 'missing-hash')], capture_output=True, text=True, timeout=20)
        assert missing_hash.returncode != 0 and 'Missing resume model hash' in missing_hash.stderr
    print('PASS resume 10 -> 3, preserved surplus/raw logs, original unchanged, seed/hash mismatch rejection')


if __name__ == '__main__':
    main()
