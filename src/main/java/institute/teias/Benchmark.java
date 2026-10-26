package institute.teias;

import institute.teias.io.FileManager;
import institute.teias.learner.*;
import institute.teias.oracles.*;
import institute.teias.oracles.perfect.PerfectTestOracle;
import net.automatalib.automaton.transducer.CompactMealy;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Runs each attempt in an isolated JVM so timeout and heap limits are enforceable. */
public class Benchmark {
    private static final Path MODEL_DIRECTORY = Path.of("src/main/resources/all_FSMs");
    private static volatile Process activeWorker;
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Process p = activeWorker;
            if (p != null && p.isAlive()) {
                p.destroy();
                try { if (!p.waitFor(2, TimeUnit.SECONDS)) p.destroyForcibly(); }
                catch (InterruptedException e) { p.destroyForcibly(); Thread.currentThread().interrupt(); }
            }
        }));
    }
    private static final String HEADER = "file,size,learnSymbols,learnResets,TestSymbols,TestResets";
    private static final String USAGE = """
            Usage: Benchmark <model-list> [options]
              --output-dir DIR       default: build/benchmark-results (fresh directory)
              --algorithms LIST      comma-separated nclsharp,clsharp,lsharp; default nclsharp,clsharp
              --repetitions N         successful runs per model/algorithm; default 10
              --max-attempts N        total attempts per model/algorithm; default 20
              --seed N                master seed; default 2027
              --timeout-seconds N     hard timeout per attempt; default 1200
              --heap-mb N             Java heap per attempt; default 1024
              --rule2 Nothing|SepSeq|Ads   default Ads
              --rule3 SepSeq|Ads           default Ads
              --cache true|false      default true
            Oracle: Hybrid-ADS. Run from the repository root.
            """;

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--worker")) { worker(args); return; }
        if (args.length == 1 && args[0].equals("--help")) { System.out.println(USAGE); return; }
        if (args.length == 0 || args.length % 2 != 1) throw new IllegalArgumentException(USAGE);
        Properties config = new Properties();
        Map<String, String> defaults = Map.ofEntries(
                Map.entry("output-dir", "build/benchmark-results"), Map.entry("algorithms", "nclsharp,clsharp"),
                Map.entry("repetitions", "10"), Map.entry("max-attempts", "20"), Map.entry("seed", "2027"),
                Map.entry("timeout-seconds", "1200"), Map.entry("heap-mb", "1024"),
                Map.entry("rule2", "Ads"), Map.entry("rule3", "Ads"), Map.entry("cache", "true"));
        defaults.forEach(config::setProperty);
        Set<String> supplied = new HashSet<>();
        for (int i = 1; i < args.length; i += 2) {
            String key = args[i].replaceFirst("^--", "");
            if (!args[i].startsWith("--") || !defaults.containsKey(key) || !supplied.add(key))
                throw new IllegalArgumentException("Unknown or duplicate option: " + args[i]);
            config.setProperty(key, args[i + 1]);
        }
        int repetitions = positive(config, "repetitions"), attempts = positive(config, "max-attempts");
        int timeout = positive(config, "timeout-seconds"), heap = positive(config, "heap-mb");
        long masterSeed = Long.parseLong(config.getProperty("seed"));
        if (attempts < repetitions) throw new IllegalArgumentException("max-attempts must be >= repetitions");
        Rule2.valueOf(config.getProperty("rule2")); Rule3.valueOf(config.getProperty("rule3"));
        if (!Set.of("true", "false").contains(config.getProperty("cache"))) throw new IllegalArgumentException("Invalid cache flag");
        List<String> algorithms = Arrays.asList(config.getProperty("algorithms").split(",", -1));
        if (new HashSet<>(algorithms).size() != algorithms.size() || algorithms.stream().anyMatch(a -> !Set.of("nclsharp", "clsharp", "lsharp").contains(a)))
            throw new IllegalArgumentException("Invalid algorithms");
        List<String> files = readModelList(Path.of(args[0]));
        Path out = Path.of(config.getProperty("output-dir")).toAbsolutePath();
        Files.createDirectories(out);
        if (Files.exists(out.resolve("run.properties"))) throw new IllegalArgumentException("Output already contains a run: " + out);
        for (String alg : algorithms) if (Files.exists(out.resolve(alg + "_results.csv")))
            throw new IllegalArgumentException("Output already exists: " + alg);
        Files.createDirectories(out.resolve("attempts"));
        config.setProperty("started", Instant.now().toString());
        config.setProperty("java-version", System.getProperty("java.version"));
        config.setProperty("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        config.setProperty("model-list", Path.of(args[0]).toAbsolutePath().toString());
        config.setProperty("oracle", "hybrid-ads -p buggy -m random -k 10 -s hads -r 10");
        config.setProperty("git-revision", revision(Path.of(".")));
        config.setProperty("hybrid-ads-revision", System.getenv().getOrDefault("HYBRID_ADS_REVISION", revision(Path.of("hybrid-ads"))));
        store(config, out.resolve("run.properties"));
        Files.write(out.resolve("models.txt"), files);
        writeHashes(out.resolve("inputs.sha256"), files);
        boolean complete = true;
        for (String alg : algorithms) Files.writeString(out.resolve(alg + "_results.csv"), HEADER + "\n");
        try (BufferedWriter log = Files.newBufferedWriter(out.resolve("attempts.csv"))) {
            log.write("file,algorithm,attempt,repetition,seed,partialSeed,status,elapsedMillis,detail\n");
            for (String file : files) {
                for (String alg : algorithms) {
                    try (BufferedWriter csv = Files.newBufferedWriter(out.resolve(alg + "_results.csv"), StandardOpenOption.APPEND)) {
                        int successes = 0;
                        for (int attempt = 1; attempt <= attempts && successes < repetitions; attempt++) {
                            // Same seed pair for corresponding attempts across algorithms; stable under list reordering.
                            Random random = new Random(masterSeed ^ ((long) file.hashCode() << 32) ^ attempt);
                            int seed = random.nextInt(), partialSeed = random.nextInt();
                            String id = file + "-" + alg + "-" + attempt;
                            Path result = out.resolve("attempts/" + id + ".properties");
                            Path detail = out.resolve("attempts/" + id + ".log");
                            long start = System.nanoTime();
                            String status;
                            Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                                    "-Xmx" + heap + "m", "-DLOG_LEVEL=WARN", "-cp", System.getProperty("java.class.path"),
                                    Benchmark.class.getName(), "--worker", out.resolve("run.properties").toString(), file, alg,
                                    Integer.toString(seed), Integer.toString(partialSeed), result.toString())
                                    .redirectErrorStream(true).redirectOutput(detail.toFile()).start();
                            activeWorker = process;
                            if (!process.waitFor(timeout, TimeUnit.SECONDS)) {
                                process.destroy(); // Worker shutdown hook terminates its native oracle.
                                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                                    try { process.descendants().forEach(h -> h.destroyForcibly()); }
                                    catch (RuntimeException ignored) { /* Some sandboxes prohibit process enumeration. */ }
                                    process.destroyForcibly(); process.waitFor();
                                }
                                status = "timeout";
                            } else if (process.exitValue() != 0) status = "error";
                            else {
                                Properties raw = load(result);
                                if (Boolean.parseBoolean(raw.getProperty("learned"))) {
                                    csv.write(file + "," + raw.getProperty("size") + ","
                                            + (Long.parseLong(raw.getProperty("learnInputs")) - Long.parseLong(raw.getProperty("minCeInputs"))) + ","
                                            + (Long.parseLong(raw.getProperty("learnResets")) - Long.parseLong(raw.getProperty("minCeResets"))) + ","
                                            + raw.getProperty("testInputs") + "," + raw.getProperty("testResets") + "\n");
                                    csv.flush(); successes++; status = "success";
                                } else status = "not_learned";
                            }
                            activeWorker = null;
                            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                            log.write(file + "," + alg + "," + attempt + "," + (status.equals("success") ? successes : successes + 1)
                                    + "," + seed + "," + partialSeed + "," + status + "," + elapsed + ",attempts/" + id + ".log\n");
                            log.flush();
                            System.out.println(file + " " + alg + " attempt " + attempt + ": " + status + " (" + successes + "/" + repetitions + ")");
                        }
                        if (successes < repetitions) complete = false;
                    }
                }
            }
        }
        config.setProperty("finished", Instant.now().toString());
        config.setProperty("complete", Boolean.toString(complete)); store(config, out.resolve("run.properties"));
        System.out.println("Results: " + out);
        if (!complete) throw new IllegalStateException("Benchmark incomplete; see attempts.csv and attempt logs");
    }

    private static void writeHashes(Path destination, List<String> models) throws Exception {
        Set<Path> paths = new TreeSet<>();
        for (String model : models) paths.add(MODEL_DIRECTORY.resolve(model));
        for (String name : List.of("build.gradle", "settings.gradle", "hybrid-ads/build/main", "build-source.sha256")) {
            Path p = Path.of(name); if (Files.isRegularFile(p)) paths.add(p);
        }
        if (Files.isDirectory(Path.of("src/main/java"))) {
            try (var stream = Files.walk(Path.of("src/main/java"))) {
                stream.filter(Files::isRegularFile).forEach(paths::add);
            }
        }
        Path code = Path.of(Benchmark.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (Files.isRegularFile(code)) paths.add(code);
        else try (var stream = Files.walk(code)) { stream.filter(Files::isRegularFile).forEach(paths::add); }
        try (BufferedWriter w = Files.newBufferedWriter(destination)) {
            for (Path path : paths) {
                var digest = java.security.MessageDigest.getInstance("SHA-256");
                try (InputStream in = Files.newInputStream(path)) {
                    byte[] buffer = new byte[65536]; int n;
                    while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
                }
                w.write(HexFormat.of().formatHex(digest.digest()) + "  " + path + "\n");
            }
        }
    }

    private static int positive(Properties p, String key) {
        int n = Integer.parseInt(p.getProperty(key));
        if (n <= 0) throw new IllegalArgumentException(key + " must be positive");
        return n;
    }
    private static Properties load(Path file) throws IOException {
        Properties p = new Properties(); try (Reader r = Files.newBufferedReader(file)) { p.load(r); } return p;
    }
    private static void store(Properties p, Path file) throws IOException {
        try (Writer w = Files.newBufferedWriter(file)) { p.store(w, "Benchmark metadata / raw counters"); }
    }
    private static String revision(Path dir) {
        try {
            Process p = new ProcessBuilder("git", "-C", dir.toString(), "rev-parse", "HEAD").redirectError(ProcessBuilder.Redirect.DISCARD).start();
            return new String(p.getInputStream().readAllBytes()).strip();
        } catch (IOException e) { return "unavailable"; }
    }
    private static void worker(String[] args) throws Exception {
        Properties config = load(Path.of(args[1]));
        CompactMealy<String, String> model = FileManager.loadMealyFromDotFile(MODEL_DIRECTORY.resolve(args[2]).toFile());
        LearnResult<?, ?> r = runTest(args[3], model, Rule2.valueOf(config.getProperty("rule2")),
                Rule3.valueOf(config.getProperty("rule3")), Boolean.parseBoolean(config.getProperty("cache")),
                Integer.parseInt(args[4]), Integer.parseInt(args[5]));
        Properties raw = new Properties();
        raw.setProperty("size", "" + model.size()); raw.setProperty("learned", "" + r.learned());
        raw.setProperty("learnInputs", "" + r.learnInputs()); raw.setProperty("learnResets", "" + r.learnResets());
        raw.setProperty("minCeInputs", "" + r.minCeInputs()); raw.setProperty("minCeResets", "" + r.minCeResets());
        raw.setProperty("testInputs", "" + r.testInputs()); raw.setProperty("testResets", "" + r.testResets());
        raw.setProperty("rounds", "" + r.rounds()); raw.setProperty("components", "" + r.components());
        raw.setProperty("maxComponentSize", "" + r.maxComponentSize());
        store(raw, Path.of(args[6]));
    }

    private static List<String> readModelList(Path listPath) throws IOException {
        List<String> files = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        int lineNumber = 0;
        for (String line : Files.readAllLines(listPath)) {
            lineNumber++;
            String file = line.strip();
            if (file.isEmpty() || file.startsWith("#")) continue;
            if (!file.matches("[A-Za-z0-9_.-]+\\.dot")) {
                throw new IllegalArgumentException("Expected a .dot filename at " + listPath + ":" + lineNumber);
            }
            if (!seen.add(file)) throw new IllegalArgumentException("Duplicate model in " + listPath + ": " + file);
            if (!Files.isRegularFile(MODEL_DIRECTORY.resolve(file))) {
                throw new IllegalArgumentException("Model does not exist: " + MODEL_DIRECTORY.resolve(file));
            }
            files.add(file);
        }
        if (files.isEmpty()) throw new IllegalArgumentException("Model list is empty: " + listPath);
        return files;
    }

    private static LearnResult<?, ?> runTest(String alg, CompactMealy<String, String> targetMealy, Rule2 rule2, Rule3 rule3, boolean cache, int seed, int partialSeed) throws IOException {
        OutputOracle<String, String> outputOracle = new OutputOracle<>(targetMealy);
        TestOracle<String, String> testOracle = new HybridAdsTestOracle<>(targetMealy, seed);
        TestOracle<String, String> partialTestOracle = new HybridAdsTestOracle<>(targetMealy, partialSeed);
        PerfectTestOracle<String, String> perfectTestOracle = new PerfectTestOracle<>(targetMealy);
        HashSet<String> alphabet = new HashSet<>(targetMealy.getInputAlphabet());

        if (alg.equals("lsharp")) {
            LSharp<String, String> lSharp = new LSharp<>(outputOracle, testOracle, perfectTestOracle, alphabet, rule2, rule3, cache);
            LearnResult<String, String> result = lSharp.learnMealy();
            return result;
        }
        if (alg.equals("nclsharp")) {
            NCLSharp<String, String> nclSharp = new NCLSharp<>(outputOracle, testOracle, partialTestOracle, perfectTestOracle, alphabet, rule2, rule3, cache);
            LearnResult<String, String> result = nclSharp.learnComponents();
            return result;
        }
        if (alg.equals("clsharp")) {
            CLSharp<String, String> clSharp = new CLSharp<>(outputOracle, testOracle, perfectTestOracle, alphabet, rule2, rule3, cache);
            LearnResult<String, String> result = clSharp.learnComponents();
            return result;
        }
        throw new IllegalArgumentException("Unknown algorithm: " + alg);
    }

}
