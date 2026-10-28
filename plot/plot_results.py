#!/usr/bin/env python3
"""Plot current six-column trial CSVs in the style of paper Figures 5 and 6.

Points: arithmetic mean per MODEL (not pooled by size).
Bars: sample SD by default; configurable SEM or 95% Student-t CI.
Inputs are already exported costs: no further minCe subtraction is performed.
"""
import argparse
import csv
import hashlib
import io
import json
import math
import platform
import statistics
import sys
import textwrap
from pathlib import Path

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.lines import Line2D
from matplotlib.ticker import ScalarFormatter
import numpy as np
import scipy
from scipy.stats import t as student_t

COLUMNS = ['file', 'size', 'learnSymbols', 'learnResets', 'TestSymbols', 'TestResets']
METRICS = COLUMNS[2:]
SERIES = {
    'clstar_no_cache': ('CL* (no cache)', '#111111'),
    'clstar_cached': ('CL* (with cache)', '#e6b800'),
    'nclsharp': ('Naive CL#', '#ff0000'),
    'clsharp': ('CL#', '#0033ff'),
    'lsharp': ('L#', '#009900'),
}
PHASES = {
    'learning': [('learnSymbols', 'Learning input symbols'), ('learnResets', 'Learning resets')],
    'testing': [('TestSymbols', 'Test input symbols'), ('TestResets', 'Test resets')],
}
BAR_LABEL = {'sd': 'sample SD', 'sem': 'SEM', 'ci95': '95% Student-t CI', 'none': 'no error bars'}


def summarize(values, errorbars):
    n = len(values)
    mean = statistics.fmean(values)
    sd = statistics.stdev(values) if n >= 2 else None
    sem = sd / math.sqrt(n) if sd is not None else None
    half = {'sd': sd, 'sem': sem, 'ci95': float(student_t.ppf(.975, n - 1)) * sem if sem is not None else None,
            'none': None}[errorbars]
    return dict(n=n, mean=mean, sd=sd, sem=sem, error=half,
                lower=mean-half if half is not None else None,
                upper=mean+half if half is not None else None)


def fit_trend(points, method):
    if method == 'none':
        return {'method': method, 'drawn': False}
    if len({x for x, _ in points}) < 3:
        return {'method': method, 'drawn': False, 'reason': 'fewer than three distinct model sizes'}
    if any(y <= 0 for _, y in points):
        raise ValueError('A logarithmic trend fit needs positive means; use --fit none for zero costs')
    x, y = np.array(points, dtype=float).T
    design = np.log10(x) if method == 'power-law' else x
    slope, intercept = np.polyfit(design, np.log10(y), 1)
    residual = np.sum((np.log10(y) - (intercept + slope*design))**2)
    total = np.sum((np.log10(y) - np.mean(np.log10(y)))**2)
    return dict(method=method, drawn=True, slope=float(slope), intercept=float(intercept),
                r_squared_log_y=float(1-residual/total) if total > 0 else None,
                models=len(points), min_size=float(min(x)), max_size=float(max(x)))


def read_series(path, key, args, allowed):
    groups = {}
    snapshot = path.read_bytes()
    with io.StringIO(snapshot.decode('utf-8-sig'), newline='') as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames != COLUMNS:
            raise ValueError(f'{path}: expected exactly {COLUMNS}; mixed historical/raw CSVs need explicit conversion')
        for line, row in enumerate(reader, 2):
            if set(row) != set(COLUMNS):
                raise ValueError(f'{path}:{line}: unexpected extra CSV fields')
            try:
                name = row['file'].strip()
                size = int(row['size'])
                values = [int(row[k]) for k in METRICS]
                if not name or size <= 0 or min(values) < 0:
                    raise ValueError('empty model, nonpositive size or negative counter')
            except (ValueError, TypeError, AttributeError) as error:
                raise ValueError(f'{path}:{line}: invalid trial row: {error}') from error
            if name not in groups:
                groups[name] = {'file': name, 'size': size, 'rows': []}
            if groups[name]['size'] != size:
                raise ValueError(f'{path}: model {name} has conflicting state counts')
            groups[name]['rows'].append(values)
    if not groups:
        raise ValueError(f'{path}: no trial rows')
    selected, excluded = {}, []
    for name, group in groups.items():
        n, size = len(group['rows']), group['size']
        if allowed is not None and name not in allowed:
            excluded.append({'file': name, 'reason': 'outside requested model manifest'})
            continue
        if not args.min_size <= size <= args.max_size or (key == 'lsharp' and size > args.max_lsharp_size):
            excluded.append({'file': name, 'reason': 'outside state-size range'})
            continue
        if n > args.expected_repeats:
            raise ValueError(f'{path}: {name} has {n} rows, expected {args.expected_repeats}; check for mixed runs or change --expected-repeats')
        if n != args.expected_repeats and not args.allow_partial:
            excluded.append({'file': name, 'reason': f'incomplete repetitions ({n}/{args.expected_repeats})'})
            continue
        selected[name] = {'file': name, 'size': size, 'n': n,
                          'metrics': {metric: summarize([r[i] for r in group['rows']], args.errorbars)
                                      for i, metric in enumerate(METRICS)}}
    if not selected:
        raise ValueError(f'{path}: no eligible models; for an explicitly incomplete preview use --allow-partial')
    metadata = {}
    config = path.parent/'run.properties'
    if config.is_file():
        metadata = dict(line.split('=', 1) for line in config.read_text().splitlines()
                        if line and not line.startswith('#') and '=' in line)
    return selected, dict(path=str(path.resolve()), sha256=hashlib.sha256(snapshot).hexdigest(),
                          parsed_models=len(groups), excluded=excluded,
                          run_metadata={k: metadata[k] for k in ('algorithms', 'repetitions', 'seed', 'complete',
                              'rust-counter-convention', 'rust-source-revision', 'hads-max-tests') if k in metadata})


def draw_axis(ax, metric, ylabel, series, args, audit):
    positive = [g['metrics'][metric]['mean'] for groups in series.values() for g in groups.values()
                if g['metrics'][metric]['mean'] > 0]
    floor = min(positive)/10 if positive else .1
    for key, groups in series.items():
        label, color = SERIES[key]
        models = sorted(groups.values(), key=lambda g: (g['size'], g['file']))
        points = []
        for group in models:
            s = group['metrics'][metric]
            x, y, error = group['size'], s['mean'], s['error']
            if args.yscale == 'log' and y <= 0:
                raise ValueError(f'{key}/{group["file"]}/{metric}: zero mean cannot be shown on log scale; use --yscale linear or symlog')
            points.append((x, y))
            if error is not None:
                lower = error
                if args.yscale == 'log' and s['lower'] <= 0:
                    lower = y-floor
                    audit['clipped_lower_bars'].append({'algorithm': key, 'file': group['file'], 'metric': metric,
                                                        'raw_lower': s['lower'], 'drawn_lower': floor})
                ax.errorbar(x, y, yerr=[[lower], [error]], fmt='o', color=color,
                            markersize=3.3, capsize=2.0, elinewidth=.75, alpha=.68, zorder=2)
            else:
                ax.plot(x, y, 'o', color=color, markersize=3.5,
                        markerfacecolor='none' if s['n'] < 2 else color, alpha=.75, zorder=2)
        fit = fit_trend(points, args.fit)
        audit['fits'].setdefault(metric, {})[key] = fit
        if fit['drawn']:
            x = np.geomspace(fit['min_size'], fit['max_size'], 200)
            transformed = np.log10(x) if fit['method'] == 'power-law' else x
            y = 10**(fit['intercept'] + fit['slope']*transformed)
            ax.plot(x, y, color=color, linewidth=2.5, alpha=.95, zorder=3)
    ax.set_xscale(args.xscale)
    ax.set_yscale(args.yscale)
    sizes = [g['size'] for groups in series.values() for g in groups.values()]
    lo, hi = min(args.min_size, min(sizes)), max(sizes)*1.04
    ax.set_xlim(lo*.97, max(hi, lo*1.1))
    ticks = [x for x in (100, 200, 300, 500, 1000, 1500, 2500, 3000) if lo <= x <= hi]
    if ticks:
        ax.set_xticks(ticks)
        ax.xaxis.set_major_formatter(ScalarFormatter())
    ax.minorticks_on()
    ax.grid(True, which='major', axis='y', color='#cccccc', linewidth=.65)
    ax.grid(True, which='minor', axis='y', color='#e5e5e5', linewidth=.45)
    ax.set_axisbelow(True)
    ax.set_xlabel('Number of States')
    ax.set_ylabel(ylabel + (' (log scale)' if args.yscale == 'log' else ''))


def render(series, args, audit):
    plt.rcParams.update({'font.family': 'DejaVu Sans', 'font.size': 10, 'axes.labelsize': 11})
    files = []
    for phase in args.phases:
        selected = {k: v for k, v in series.items() if phase == 'learning' or not k.startswith('clstar_')}
        if not selected:
            audit['warnings'].append(f'{phase}: no eligible series; no empty figure generated')
            continue
        partial = any(g['n'] < args.expected_repeats for v in selected.values() for g in v.values())
        partial = partial or any(audit['inputs'][k]['run_metadata'].get('complete') == 'false'
                                or any('incomplete repetitions' in item['reason'] for item in audit['inputs'][k]['excluded'])
                                for k in selected)
        footer = f'Points: per-model arithmetic means; bars: {BAR_LABEL[args.errorbars]}.'
        if partial:
            footer += ' PREVIEW: incomplete repetitions included; hollow points have n=1.'
        if args.yscale == 'log':
            footer += ' Nonpositive lower bar bounds are clipped; raw bounds are in plot_report.json.'
        handles = [Line2D([0], [0], color=SERIES[k][1], linewidth=3, label=SERIES[k][0]) for k in selected]
        fig, axes = plt.subplots(1, 2, figsize=(15, 6))
        for ax, (metric, label) in zip(axes, PHASES[phase]):
            draw_axis(ax, metric, label, selected, args, audit)
        fig.subplots_adjust(left=.07, right=.99, top=.91, bottom=.23, wspace=.23)
        fig.suptitle(('Learning phase' if phase == 'learning' else 'Test equivalence phase') + (' — preview' if partial else ''))
        fig.legend(handles=handles, loc='lower center', bbox_to_anchor=(.5, .08), ncol=len(handles), frameon=True)
        fig.text(.02, .015, textwrap.fill(footer, 155), fontsize=7.5, va='bottom')
        prefix = 'figure5_learning' if phase == 'learning' else 'figure6_testing'
        target = args.output_dir/f'{prefix}.png'
        fig.savefig(target, dpi=args.dpi, bbox_inches='tight')
        files.append(str(target))
        plt.close(fig)
        if not args.no_individual:
            for metric, label in PHASES[phase]:
                fig, ax = plt.subplots(figsize=(9, 5.5))
                # Use a separate audit so combined/individual renders do not double-count clipped bars.
                draw_axis(ax, metric, label, selected, args, {'fits': {}, 'clipped_lower_bars': []})
                ax.legend(handles=handles, loc='lower right', frameon=True)
                fig.subplots_adjust(left=.12, right=.98, top=.97, bottom=.19)
                fig.text(.02, .015, textwrap.fill(footer, 115), fontsize=7, va='bottom')
                prefix = {'learnSymbols': 'learning_symbols', 'learnResets': 'learning_resets',
                          'TestSymbols': 'testing_symbols', 'TestResets': 'testing_resets'}[metric]
                target = args.output_dir/f'{prefix}.png'
                fig.savefig(target, dpi=args.dpi, bbox_inches='tight')
                files.append(str(target))
                plt.close(fig)
    return files


def parser():
    p = argparse.ArgumentParser(description=__doc__)
    for key, (label, _) in SERIES.items():
        flag = '--clstar-cache' if key == 'clstar_cached' else '--'+key.replace('_', '-')
        p.add_argument(flag, dest=key, type=Path, help=f'six-column trial CSV for {label}')
    p.add_argument('--models', type=Path, help='optional manifest restricting model filenames')
    p.add_argument('--output-dir', type=Path, default=Path(__file__).resolve().parent/'figures')
    p.add_argument('--errorbars', choices=BAR_LABEL, default='sd')
    p.add_argument('--expected-repeats', type=int, default=10)
    p.add_argument('--allow-partial', action='store_true', help='explicit preview; n=1 has no invented error bar')
    p.add_argument('--min-size', type=int, default=100)
    p.add_argument('--max-size', type=int, default=3000)
    p.add_argument('--max-lsharp-size', type=int, default=300)
    p.add_argument('--xscale', choices=('log', 'linear'), default='log')
    p.add_argument('--yscale', choices=('log', 'linear', 'symlog'), default='log')
    p.add_argument('--fit', choices=('power-law', 'semilog', 'none'), default='power-law')
    p.add_argument('--phases', default='learning,testing', help='learning,testing or either phase')
    p.add_argument('--dpi', type=int, default=300)
    p.add_argument('--no-individual', action='store_true')
    return p


def main(argv=None):
    p = parser()
    args = p.parse_args(argv)
    args.phases = list(dict.fromkeys(args.phases.split(',')))
    if not set(args.phases) <= set(PHASES):
        p.error('invalid phases')
    if args.expected_repeats < 1 or args.dpi < 1 or not 0 < args.min_size <= args.max_size or args.max_lsharp_size < 1:
        p.error('invalid repetition count, DPI or size range')
    inputs = {key: getattr(args, key) for key in SERIES if getattr(args, key) is not None}
    if not inputs:
        p.print_help()
        return None
    allowed = None
    if args.models:
        names = [s.strip() for s in args.models.read_text().splitlines() if s.strip() and not s.strip().startswith('#')]
        if not names or len(set(names)) != len(names):
            p.error('model manifest must be nonempty and contain no duplicates')
        allowed = set(names)
    series, input_audit = {}, {}
    for key, path in inputs.items():
        series[key], input_audit[key] = read_series(path, key, args, allowed)
    sizes = {}
    for groups in series.values():
        for model, group in groups.items():
            if model in sizes and sizes[model] != group['size']:
                raise ValueError(f'Conflicting size across algorithms: {model}')
            sizes[model] = group['size']
    primary = [set(groups) for key, groups in series.items() if key != 'lsharp']
    common = set.intersection(*primary) if primary else set(series['lsharp'])
    if not common:
        raise ValueError('No shared eligible models across the supplied compositional algorithms')
    for key in series:
        removed = set(series[key])-common
        input_audit[key]['excluded'].extend({'file': m, 'reason': 'outside common compositional cohort'} for m in sorted(removed))
        series[key] = {m: g for m, g in series[key].items() if m in common}
        if not series[key]:
            raise ValueError(f'{key}: no shared models after cohort matching')
    warnings = []
    if any(g['n'] == 1 for groups in series.values() for g in groups.values()):
        warnings.append('Some points have one repetition: sample spread and confidence intervals are undefined, so no error bar is drawn.')
    report = dict(method=dict(center='arithmetic mean per model', errorbars=args.errorbars, sd_ddof=1,
                             ci95='Student-t, two-sided 95%, df=n-1', expected_repeats=args.expected_repeats,
                             fit=args.fit, xscale=args.xscale, yscale=args.yscale,
                             input_costs='plotted as exported; no further minCe subtraction',
                             cohort=f'intersection of supplied compositional models; L# restricted to its available <={args.max_lsharp_size}-state subset',
                             min_size=args.min_size, max_size=args.max_size, max_lsharp_size=args.max_lsharp_size,
                             original_plot_code='unavailable; statistical choices are explicit, not asserted identical to the original figures'),
                  script_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                  software=dict(python=platform.python_version(), matplotlib=matplotlib.__version__, numpy=np.__version__, scipy=scipy.__version__),
                  inputs=input_audit, series=series, warnings=warnings, fits={}, clipped_lower_bars=[])
    args.output_dir.mkdir(parents=True, exist_ok=True)
    report['outputs'] = render(series, args, report)
    if not report['outputs']:
        raise ValueError('No eligible figures for the selected phases; CL* is excluded from testing')
    (args.output_dir/'plot_report.json').write_text(json.dumps(report, indent=2, allow_nan=False)+'\n')
    for warning in warnings:
        print('NOTE:', warning, file=sys.stderr)
    print('Models:', ', '.join(f'{SERIES[k][0]}={len(v)}' for k, v in series.items()))
    print('Figures and statistics:', args.output_dir.resolve())
    return report


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError) as error:
        print('ERROR:', error, file=sys.stderr)
        sys.exit(2)
