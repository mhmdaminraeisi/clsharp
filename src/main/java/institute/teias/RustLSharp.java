package institute.teias;

import institute.teias.io.FileManager;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.HexFormat;
import java.security.MessageDigest;

/** Adapter for the unchanged original Rust CLI, not the local Java LSharp learner. */
final class RustLSharp {
    private static final List<String> HEADER = List.of("name", "learned", "rounds", "num_states", "num_inputs",
            "learn_inputs", "learn_resets", "test_inputs", "test_resets", "ads_score", "learning_algorithm");

    static void configure(Properties config) throws Exception {
        Path binary = Path.of(config.getProperty("rust-lsharp-executable")).toAbsolutePath();
        if (!Files.isRegularFile(binary) || !Files.isExecutable(binary))
            throw new IllegalArgumentException("Original Rust LSharp executable missing: " + binary + "; build Dockerfile.lsharp first");
        config.setProperty("rust-lsharp-executable", binary.toString());
        config.setProperty("rust-binary-sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(binary))));
        Path buildInfo = binary.resolveSibling("build.properties");
        Properties info = new Properties();
        if (Files.isRegularFile(buildInfo)) try (var reader = Files.newBufferedReader(buildInfo)) { info.load(reader); }
        config.setProperty("rust-source-revision", info.getProperty("sourceRevision", "unavailable"));
        config.setProperty("rust-cargo-lock-sha256", info.getProperty("cargoLockSha256", "unavailable"));
        config.setProperty("rust-toolchain", info.getProperty("rustc", "unavailable"));
        config.setProperty("rust-oracle", "hads-int; infinite; internal Rust implementation");
        config.setProperty("rust-counter-convention", "upstream-unadjusted");
    }

    static Path workDirectory(Path output, String id) { return output.resolve("attempts").resolve(id); }

    static List<String> command(Properties config, Path model, int seed, Path work) throws IOException {
        // Upstream deletes ./hypothesis and writes log/ and resumable_config/ itself.
        // Each trial must have its own working directory, never the source checkout.
        Files.createDirectories(work.resolve("log"));
        Files.createDirectories(work.resolve("resumable_config"));
        List<String> command = new ArrayList<>(List.of(config.getProperty("rust-lsharp-executable"),
                "-e", "hads-int", "--rule2", rule(config.getProperty("rule2")),
                "--rule3", rule(config.getProperty("rule3")), "-k", config.getProperty("rust-extra-states"),
                "-l", config.getProperty("rust-random-length"), "-m", model.toAbsolutePath().toString(),
                "--eq-mode", "infinite", "--out", work.resolve("upstream.csv").toAbsolutePath().toString(),
                "-x", Long.toString(Integer.toUnsignedLong(seed)), "-v", "0", "-q"));
        if (Boolean.parseBoolean(config.getProperty("rust-cache-rules"))) command.add("--cache-rules");
        return command;
    }

    static void importTrial(Path previous, Path output, Path detail) throws IOException {
        String logName = detail.toString();
        if (!logName.endsWith(".log")) throw new IOException("Invalid Rust trial log path");
        Path relativeWork = Path.of(logName.substring(0, logName.length() - 4));
        Path source = previous.resolve(relativeWork), destination = output.resolve(relativeWork);
        if (!Files.isDirectory(source)) throw new IOException("Missing original Rust trial directory: " + source);
        try (var stream = Files.walk(source)) {
            for (Path path : stream.toList()) {
                Path copy = destination.resolve(source.relativize(path));
                if (Files.isDirectory(path)) Files.createDirectories(copy);
                else Files.copy(path, copy);
            }
        }
        Path rawFile = output.resolve(logName.replace(".log", ".properties"));
        if (Files.isRegularFile(rawFile)) {
            Properties raw = new Properties();
            try (var reader = Files.newBufferedReader(rawFile)) { raw.load(reader); }
            raw.setProperty("upstreamCsv", destination.resolve("upstream.csv").toString());
            try (Writer writer = Files.newBufferedWriter(rawFile)) { raw.store(writer, "Imported original Rust counters; relocated upstream CSV"); }
        }
    }

    private static String rule(String name) {
        return switch (name) { case "Ads" -> "ads"; case "SepSeq" -> "sep-seq"; case "Nothing" -> "nothing";
            default -> throw new IllegalArgumentException("Unsupported Rust rule: " + name); };
    }

    static void capture(Path model, int seed, Path work, Path result) throws IOException {
        List<String> lines = Files.readAllLines(work.resolve("upstream.csv"));
        if (lines.size() != 2 || !List.of(lines.get(0).split(",", -1)).equals(HEADER))
            throw new IOException("Unexpected original Rust LSharp CSV schema/row count");
        String[] row = lines.get(1).split(",", -1);
        if (row.length != HEADER.size() || !row[0].equals(model.getFileName().toString())
                || !List.of("true", "false").contains(row[1])) throw new IOException("Invalid original Rust LSharp result");
        Properties raw = new Properties();
        raw.setProperty("learned", row[1]);
        try { raw.setProperty("size", "" + FileManager.loadMealyFromDotFile(model.toFile()).size()); }
        catch (Exception e) { throw new IOException("Cannot load input model for Rust result: " + model, e); }
        Map<String, Integer> fields = Map.of("rounds", 2, "hypothesisStates", 3, "numInputs", 4,
                "learnInputs", 5, "learnResets", 6, "testInputs", 7, "testResets", 8);
        for (var field : fields.entrySet()) {
            long value = Long.parseLong(row[field.getValue()]);
            if (value < 0) throw new IOException("Negative Rust counter: " + field.getKey());
            raw.setProperty(field.getKey(), Long.toString(value));
        }
        raw.setProperty("components", "1");
        raw.setProperty("adsScore", row[9]);
        raw.setProperty("learningAlgorithm", row[10]);
        raw.setProperty("nativeSeed", Long.toString(Integer.toUnsignedLong(seed)));
        raw.setProperty("counterConvention", "upstream-unadjusted");
        raw.setProperty("minCeAvailable", "false");
        raw.setProperty("upstreamCsv", work.resolve("upstream.csv").toString());
        // Upstream does not export a separate minimization counter. Preserve its
        // counters; do not invent a zero measurement or subtract costs twice.
        try (Writer writer = Files.newBufferedWriter(result)) { raw.store(writer, "Original Rust LSharp counters (unchanged)"); }
    }
}
