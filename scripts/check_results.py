#!/usr/bin/env python3
"""Validate exported costs against raw counters and compare means to historical data.

Usage: python3 scripts/check_results.py OUTPUT_DIR [--baseline test_results.csv]
Outputs a JSON report to stdout. Historical learning costs exclude minCe here only.
"""
import argparse
import csv
import json
from collections import defaultdict
from pathlib import Path
from statistics import mean


def rows(path):
    with path.open(newline='') as stream:
        return list(csv.DictReader(stream))


def properties(path):
    return dict(line.split('=', 1) for line in path.read_text().splitlines()
                if line and not line.startswith('#') and '=' in line)


def check(output, baseline):
    attempts = rows(output / 'attempts.csv')
    config = properties(output / 'run.properties')
    models = (output / 'models.txt').read_text().splitlines()
    report = {'complete': config.get('complete') == 'true', 'attempts': len(attempts),
              'statuses': {}, 'comparison': []}
    for a in attempts:
        status = a['status']
        report['statuses'][status] = report['statuses'].get(status, 0) + 1
        assert (output / a['detail']).is_file(), f'Missing attempt log: {a}'
    history = defaultdict(list)
    if baseline:
        for row in rows(baseline):
            if row['learned'].lower() == 'true':
                history[row['file'], row['algorithm'].removesuffix('-ADS')].append(row)
    metrics = ['learnSymbols', 'learnResets', 'TestSymbols', 'TestResets']
    for alg in config['algorithms'].split(','):
        path = output / f'{alg}_results.csv'
        with path.open(newline='') as stream:
            assert next(csv.reader(stream)) == ['file', 'size'] + metrics
        actual = rows(path)
        success = [a for a in attempts if a['algorithm'] == alg and a['status'] == 'success']
        assert len(actual) == len(success), 'Success/export count mismatch'
        grouped = defaultdict(list)
        for row, attempt in zip(actual, success):
            assert row['file'] == attempt['file']
            raw = properties(output / attempt['detail'].replace('.log', '.properties'))
            expected = [int(raw['learnInputs']) - int(raw['minCeInputs']),
                        int(raw['learnResets']) - int(raw['minCeResets']),
                        int(raw['testInputs']), int(raw['testResets'])]
            assert raw['learned'] == 'true' and int(row['size']) == int(raw['size'])
            assert [int(row[k]) for k in metrics] == expected
            assert all(v >= 0 for v in expected)
            if alg.startswith('clstar-'):
                assert raw['cache'] == str(alg == 'clstar-cached').lower()
                assert raw['referencePrecheck'] == 'false'
                assert int(raw['testInputsIncludingFinalEq']) == int(raw['testInputs']) + int(raw['excludedFinalEqInputs'])
                assert int(raw['testResetsIncludingFinalEq']) == int(raw['testResets']) + int(raw['excludedFinalEqResets'])
                assert int(raw['minCeInputs']) <= int(raw['learnInputs'])
                assert int(raw['minCeResets']) <= int(raw['learnResets'])
            grouped[row['file']].append(row)
        if report['complete']:
            assert set(grouped) == set(models)
            assert all(len(g) == int(config['repetitions']) for g in grouped.values())
        for model, data in grouped.items():
            old = history[model, alg]
            if not old:
                continue
            for metric in metrics:
                deduction = {'learnSymbols': 'minCeSymbols', 'learnResets': 'minCeResets'}.get(metric)
                old_mean = mean(int(r[metric]) - (int(r[deduction]) if deduction else 0) for r in old)
                new_mean = mean(int(r[metric]) for r in data)
                report['comparison'].append({'file': model, 'algorithm': alg, 'metric': metric,
                    'historical_mean': old_mean, 'current_mean': new_mean,
                    'ratio': new_mean / old_mean if old_mean else None,
                    'historical_samples': len(old), 'current_samples': len(data)})
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('output', type=Path)
    parser.add_argument('--baseline', type=Path)
    args = parser.parse_args()
    print(json.dumps(check(args.output, args.baseline), indent=2))
