# CL* provenance

The CL* algorithm (`MealyLearnInParts`, `ProductMealy`, DOT loader and perfect
validation helpers) comes from Faezeh Labbaf's project:
<https://github.com/faezeh-lbf/CL-Star>, accompanying *Compositional Learning for
Interleaving Parallel Automata*, supervised by Hossein Hojjat and Mohammad Mousavi.

The supplied checkout's HEAD was `899d16834f4bc96fc997d3873aecbab22577a020`.
It also contained local modifications; that revision alone is not a complete
identifier for the imported working tree. `imported-sources.sha256` records the
original bytes of the retained algorithm/helper files before integration edits.
No upstream LICENSE file was present in the supplied checkout; this notice
preserves attribution and does not relicense the upstream work under the root MIT
license. LearnLib's own license applies to the downloaded dependency bundle.

Integration changes:

- Removed old experiments, result CSVs, models, notebooks, compiled files, IDE/Git
  metadata, bundled dependencies and the duplicate native Hybrid-ADS checkout.
- Preserved CL* decomposition, dependency search, L* learning and product logic;
  removed commented debug/examples and unused loading/reporting utilities.
- Consolidated the HADS adapters into one oracle routing every target query through
  the selected LearnLib SUL stack; uses the root project's native executable.
- Added actual SUL counter deltas around counterexample minimization, including
  prefix cutting. Original logical subset-query counters remain available for audit.
- Added an isolated trial entry point, paired cache modes, failure handling,
  independent final validation and the common benchmark runner/export format.

The imported dependency bundle is LearnLib 0.16.0 with SHA-256:
`a60d4a7ca277cb0e5e11745ab584041d10825b841e82535d9f4597b46a705a6f`.
Normal builds resolve the version-pinned Maven artifact; offline builds may supply
that exact bundle with `-PclstarDependencyJar=/absolute/path/to/the.jar`.
