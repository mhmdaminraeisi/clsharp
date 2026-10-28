#!/usr/bin/env python3
"""Statistical and data-integrity checks for the paper-style plotting pipeline."""
import csv
import math
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]/'plot'))
import plot_results as plot


class PlotChecks(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='paper-plot-check-')
        self.root = Path(self.tmp.name)
        self.args = plot.parser().parse_args([])

    def tearDown(self):
        self.tmp.cleanup()

    def csv(self, name, groups):
        path = self.root/name
        with path.open('w', newline='') as stream:
            writer = csv.writer(stream)
            writer.writerow(plot.COLUMNS)
            for model, size, values in groups:
                for value in values:
                    writer.writerow([model, size, value, value, value, value])
        return path

    def test_each_algorithm_can_be_supplied_alone(self):
        source = self.csv('one.csv', [('65.dot', 112, [100]*10)])
        for flag in ('--lsharp', '--clsharp', '--nclsharp', '--clstar-cache', '--clstar-no-cache'):
            with self.subTest(flag=flag):
                report = plot.main([flag, str(source), '--dpi', '30', '--no-individual',
                                    '--output-dir', str(self.root/flag[2:])])
                self.assertEqual(len(report['series']), 1)
                self.assertTrue(all(Path(path).suffix == '.png' for path in report['outputs']))

    def test_mean_sample_sd_sem_and_student_ci(self):
        values = list(range(1, 11))
        result = plot.summarize(values, 'sd')
        self.assertEqual(result['mean'], 5.5)
        self.assertAlmostEqual(result['sd'], math.sqrt(55/6))
        self.assertAlmostEqual(plot.summarize(values, 'sem')['error'], math.sqrt(55/60))
        self.assertAlmostEqual(plot.summarize(values, 'ci95')['error'], 2.2621571628540993*math.sqrt(55/60))
        one = plot.summarize([7], 'ci95')
        self.assertIsNone(one['sd'])
        self.assertIsNone(one['error'])

    def test_models_with_equal_sizes_are_not_pooled(self):
        path = self.csv('same-size.csv', [('65.dot', 112, [10]*10), ('66.dot', 112, [100]*10)])
        data, _ = plot.read_series(path, 'nclsharp', self.args, None)
        self.assertEqual(len(data), 2)
        self.assertEqual(data['65.dot']['metrics']['learnSymbols']['mean'], 10)
        self.assertEqual(data['66.dot']['metrics']['learnSymbols']['mean'], 100)

    def test_partial_repetitions_need_explicit_preview(self):
        path = self.csv('partial.csv', [('65.dot', 112, [10])])
        with self.assertRaisesRegex(ValueError, 'no eligible models'):
            plot.read_series(path, 'nclsharp', self.args, None)
        self.args.allow_partial = True
        data, _ = plot.read_series(path, 'nclsharp', self.args, None)
        self.assertIsNone(data['65.dot']['metrics']['learnSymbols']['error'])
        over = self.csv('over.csv', [('65.dot', 112, [10]*11)])
        with self.assertRaisesRegex(ValueError, '11 rows'):
            plot.read_series(over, 'nclsharp', self.args, None)

    def test_conflicting_sizes_and_negative_counts_are_errors(self):
        mixed = self.csv('mixed.csv', [('65.dot', 112, [10]*5), ('65.dot', 113, [10]*5)])
        with self.assertRaisesRegex(ValueError, 'conflicting state counts'):
            plot.read_series(mixed, 'nclsharp', self.args, None)
        negative = self.csv('negative.csv', [('65.dot', 112, [-1]*10)])
        with self.assertRaisesRegex(ValueError, 'negative counter'):
            plot.read_series(negative, 'nclsharp', self.args, None)

    def test_known_power_law_and_no_two_point_fit(self):
        fit = plot.fit_trend([(100, 10000), (200, 40000), (400, 160000)], 'power-law')
        self.assertAlmostEqual(fit['slope'], 2)
        self.assertAlmostEqual(fit['intercept'], 0)
        self.assertAlmostEqual(fit['r_squared_log_y'], 1)
        self.assertFalse(plot.fit_trend([(100, 100), (200, 200)], 'power-law')['drawn'])

    def test_rendering_matches_cohorts_and_excludes_clstar_from_testing(self):
        ncl = self.csv('ncl.csv', [('65.dot', 112, [0]*9+[100]), ('66.dot', 128, [20]*10)])
        star = self.csv('star.csv', [('65.dot', 112, [30]*10)])
        report = plot.main(['--nclsharp', str(ncl), '--clstar-cache', str(star),
                            '--dpi', '50', '--no-individual', '--output-dir', str(self.root/'plots')])
        self.assertEqual(set(report['series']['nclsharp']), {'65.dot'})
        self.assertEqual(set(report['fits']['TestSymbols']), {'nclsharp'})
        self.assertIn('clstar_cached', report['fits']['learnSymbols'])
        self.assertTrue(report['clipped_lower_bars'])
        self.assertLess(report['series']['nclsharp']['65.dot']['metrics']['learnSymbols']['lower'], 0)
        self.assertTrue(all(Path(path).is_file() and Path(path).suffix == '.png' for path in report['outputs']))
        self.assertFalse(list((self.root/'plots').glob('*.pdf')))

    def test_lsharp_partial_preview_never_invents_error_bars(self):
        source = self.csv('lsharp.csv', [('65.dot', 112, [100]), ('94.dot', 288, [1000])])
        report = plot.main(['--lsharp', str(source), '--allow-partial', '--dpi', '40',
                            '--no-individual', '--output-dir', str(self.root/'preview')])
        for model in report['series']['lsharp'].values():
            self.assertIsNone(model['metrics']['learnSymbols']['sd'])
        self.assertFalse(report['fits']['learnSymbols']['lsharp']['drawn'])


if __name__ == '__main__':
    unittest.main()
