#!/usr/bin/env python3
"""Check Docker argument defaults, overrides and the original native CLI escape."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ENTRYPOINT = Path(__file__).resolve().parents[1]/'docker/run-benchmark.sh'


class EntrypointChecks(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='benchmark-entrypoint-')
        self.root = Path(self.tmp.name)
        self.capture = self.root/'arguments.json'
        binary = self.root/'java'
        binary.write_text(f'#!{sys.executable}\nimport json,os,sys\n'
                          'from pathlib import Path\n'
                          'Path(os.environ["CAPTURE"]).write_text(json.dumps([sys.argv[1:],os.getcwd()]))\n')
        binary.chmod(0o755)
        self.env = dict(os.environ, PATH=str(self.root)+os.pathsep+os.environ['PATH'],
                        BENCHMARK_MODELS='benchmarks/paper-models.txt',
                        BENCHMARK_ALGORITHMS='nclsharp,clsharp', BENCHMARK_OUTPUT=str(self.root/'results'),
                        CAPTURE=str(self.capture))

    def tearDown(self):
        self.tmp.cleanup()

    def run_script(self, *args):
        subprocess.run(['sh', str(ENTRYPOINT), *args], env=self.env, check=True, capture_output=True)
        return json.loads(self.capture.read_text())

    def test_defaults_and_all_family_algorithms(self):
        for alg in ('nclsharp,clsharp', 'clstar-cached,clstar-no-cache', 'lsharp-rust'):
            self.env['BENCHMARK_ALGORITHMS'] = alg
            args, _ = self.run_script()
            self.assertEqual(args[-5:], ['benchmarks/paper-models.txt', '--algorithms', alg,
                                         '--output-dir', self.env['BENCHMARK_OUTPUT']])

    def test_custom_options_and_manifest_pass_through(self):
        args, _ = self.run_script('/models.txt', '--seed', '42', '--repetitions', '3', '--rule2', 'SepSeq',
                                  '--algorithms', 'clsharp', '--output-dir', '/results/new')
        self.assertEqual(args[5:], ['/models.txt', '--seed', '42', '--repetitions', '3', '--rule2', 'SepSeq',
                                    '--algorithms', 'clsharp', '--output-dir', '/results/new'])
        self.assertEqual(args.count('--algorithms'), 1)
        self.assertEqual(args.count('--output-dir'), 1)

    def test_options_without_manifest_and_help(self):
        args, _ = self.run_script('--seed', '42')
        self.assertEqual(args[5:8], ['benchmarks/paper-models.txt', '--seed', '42'])
        args, _ = self.run_script('--help')
        self.assertEqual(args[5:], ['--help'])

    def test_native_cli_has_isolated_work_directory(self):
        self.env['LSHARP_RUST_EXECUTABLE'] = str(self.root/'java')
        args, cwd = self.run_script('--native', '--help')
        self.assertEqual(args, ['--help'])
        self.assertEqual(Path(cwd).resolve(), (Path(self.env['BENCHMARK_OUTPUT'])/'native').resolve())
        self.assertTrue((Path(cwd)/'log').is_dir())
        self.assertTrue((Path(cwd)/'resumable_config').is_dir())


if __name__ == '__main__':
    unittest.main()
