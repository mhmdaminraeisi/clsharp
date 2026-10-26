package oracle;

import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import net.automatalib.automata.transducers.MealyMachine;
import net.automatalib.serialization.dot.GraphDOT;
import net.automatalib.words.Word;
import net.automatalib.words.WordBuilder;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shared native HADS generator; every target query passes through the supplied MQ/EQ stack. */
public final class HybridAdsTestOracle implements EquivalenceOracle.MealyEquivalenceOracle<String, Word<String>> {
    private static final Set<Process> ACTIVE = ConcurrentHashMap.newKeySet();
    static { Runtime.getRuntime().addShutdownHook(new Thread(() -> ACTIVE.forEach(Process::destroyForcibly))); }
    private final MembershipOracle<String, Word<Word<String>>> target;
    private final Path executable;
    private final Random random;
    private final int maxTests;
    private final long timeoutSeconds;
    private long calls, tests;
    private final List<Long> seeds = new ArrayList<>();

    public static Path defaultExecutable() {
        return Path.of(System.getProperty("hybrid.ads.executable", "hybrid-ads/build/main")).toAbsolutePath();
    }
    public HybridAdsTestOracle(MembershipOracle<String, Word<Word<String>>> target,
            Path executable, long seed, int maxTests, long timeoutSeconds) {
        if (maxTests <= 0 || timeoutSeconds <= 0) throw new IllegalArgumentException("HADS budgets must be positive");
        this.target = target; this.executable = executable.toAbsolutePath();
        this.random = new Random(seed); this.maxTests = maxTests; this.timeoutSeconds = timeoutSeconds;
    }
    public long getCalls() { return calls; }
    public long getTests() { return tests; }
    public List<Long> getSeeds() { return List.copyOf(seeds); }

    @Override
    public DefaultQuery<String, Word<Word<String>>> findCounterExample(
            MealyMachine<?, String, ?, Word<String>> hypothesis, Collection<? extends String> inputs) {
        calls++;
        Path model = null, errors = null;
        Process process = null;
        ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();
        AtomicBoolean timedOut = new AtomicBoolean(false);
        try {
            model = Files.createTempFile("clstar-hads-", ".dot");
            errors = Files.createTempFile("clstar-hads-", ".err");
            try (Writer writer = Files.newBufferedWriter(model)) { GraphDOT.write(hypothesis, inputs, writer); }
            long seed = Integer.toUnsignedLong(random.nextInt());
            if (seed == 0) seed = 1;
            seeds.add(seed);
            // -e permits duplicate generated words, avoiding exhaustion over tiny component alphabets.
            process = new ProcessBuilder(executable.toString(), "-p", "buggy", "-m", "random", "-k", "10",
                    "-s", "hads", "-r", "10", "-x", Long.toString(seed), "-f", model.toString(), "-e")
                    .redirectError(errors.toFile()).start();
            ACTIVE.add(process);
            final Process child = process;
            watchdog.schedule(() -> { timedOut.set(true); child.destroyForcibly(); }, timeoutSeconds, TimeUnit.SECONDS);
            int count = 0;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (timedOut.get()) throw new IOException("HADS exceeded oracle timeout");
                    if (line.isBlank()) continue;
                    WordBuilder<String> builder = new WordBuilder<>();
                    for (String symbol : line.strip().split("\\s+")) {
                        if (!inputs.contains(symbol)) throw new IOException("Unknown HADS input: " + symbol);
                        builder.append(symbol);
                    }
                    Word<String> word = builder.toWord();
                    Word<Word<String>> actual = target.answerQuery(word);
                    Word<Word<String>> expected = hypothesis.computeOutput(word);
                    tests++; count++;
                    if (!actual.equals(expected)) return new DefaultQuery<>(word, actual);
                    if (count >= maxTests) return null;
                }
            }
            if (timedOut.get()) throw new IOException("HADS exceeded oracle timeout");
            int exit = process.waitFor();
            throw new IOException("HADS ended before its test budget (exit " + exit + "): " + Files.readString(errors));
        } catch (IOException e) {
            throw new IllegalStateException("Hybrid-ADS oracle failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("Hybrid-ADS interrupted", e);
        } finally {
            watchdog.shutdownNow();
            if (process != null) {
                process.destroyForcibly(); ACTIVE.remove(process);
                try { process.waitFor(5, TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            try { if (model != null) Files.deleteIfExists(model); } catch (IOException ignored) { }
            try { if (errors != null) Files.deleteIfExists(errors); } catch (IOException ignored) { }
        }
    }
}
