#!/usr/bin/env python3
"""Check original-Rust CLI integration using a controlled native executable fixture.
Run after installDist: python3 scripts/test_rust_lsharp.py --classpath 'build/install/nclsharp/lib/*'
Real upstream smoke tests are documented in baseline/lsharp-support/README.md.
"""
import argparse
import csv
import subprocess
import tempfile
from pathlib import Path
from check_results import check, properties


def fake_binary(path, malformed=False):
    row = 'wrong-header' if malformed else 'name,learned,rounds,num_states,num_inputs,learn_inputs,learn_resets,test_inputs,test_resets,ads_score,learning_algorithm'
    text = '''#!/bin/sh
while [ "$#" -gt 0 ]; do
 case "$1" in
  --out) out="$2"; shift 2 ;;
  -m) model="$2"; shift 2 ;;
  -x) seed="$2"; shift 2 ;;
  *) shift ;;
 esac
done
[ -d log ] && [ -d resumable_config ] || exit 12
printf '%s\\n' "$PWD" "$RAYON_NUM_THREADS" "$seed"
printf '%s\\n' 'HEADER' "${model##*/},true,2,2,2,1000,100,200,20,0.5,lsharp_Ads_Ads" > "$out"
'''.replace('HEADER', row)
    path.write_text(text)
    path.chmod(0o755)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--classpath', required=True)
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix='rust-lsharp-cli-') as tmp:
        root = Path(tmp)
        model_list = root / 'models.txt'
        model_list.write_text('1.dot\n')
        binary = root / 'fake-lsharp'
        fake_binary(binary)
        base = ['java', '-DLOG_LEVEL=WARN', '-cp', args.classpath, 'institute.teias.Benchmark', str(model_list),
                '--algorithms', 'lsharp-rust', '--rust-lsharp-executable', str(binary), '--timeout-seconds', '10']
        first = root / 'first'
        subprocess.run(base + ['--repetitions', '2', '--output-dir', str(first)], check=True, capture_output=True, text=True, timeout=30)
        report = check(first, None)
        assert report['complete'] and report['statuses'] == {'success': 2}
        rows = list(csv.DictReader((first / 'lsharp-rust_results.csv').open()))
        assert len(rows) == 2 and all(r['size'] == '4' and r['learnSymbols'] == '1000' and r['learnResets'] == '100' for r in rows)
        raw = properties(first / 'attempts/1.dot-lsharp-rust-1.properties')
        assert raw['hypothesisStates'] == '2' and raw['minCeAvailable'] == 'false' and 'minCeInputs' not in raw
        assert 0 <= int(raw['nativeSeed']) <= 2**32 - 1
        assert (first / 'attempts/1.dot-lsharp-rust-1/resumable_config').is_dir()
        resumed = root / 'resumed'
        subprocess.run(base + ['--repetitions', '3', '--resume-from', str(first), '--output-dir', str(resumed)], check=True, capture_output=True, text=True, timeout=30)
        assert check(resumed, None)['statuses'] == {'success': 3}
        # Invalid native output must be an explicit failed attempt, never a zero/success row.
        fake_binary(binary, malformed=True)
        invalid = root / 'invalid'
        result = subprocess.run(base + ['--repetitions', '1', '--max-attempts', '1', '--output-dir', str(invalid)], capture_output=True, text=True, timeout=30)
        assert result.returncode != 0
        assert check(invalid, None)['statuses'] == {'error': 1}
        assert 'Unexpected original Rust LSharp CSV' in (invalid / 'attempts/1.dot-lsharp-rust-1.log').read_text()
    print('PASS Rust adapter: raw counts preserved, input size independent of hypothesis size, isolated workdirs, unsigned seed, resume and invalid-output rejection')


if __name__ == '__main__':
    main()
