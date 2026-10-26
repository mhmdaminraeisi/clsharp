import de.learnlib.api.SUL;
import de.learnlib.api.oracle.*;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.api.statistic.StatisticSUL;
import de.learnlib.driver.util.MealySimulatorSUL;
import de.learnlib.filter.cache.sul.SULCache;
import de.learnlib.filter.statistic.sul.*;
import de.learnlib.oracle.membership.SULOracle;
import net.automatalib.automata.transducers.impl.compact.CompactMealy;
import net.automatalib.words.*;
import net.automatalib.words.impl.Alphabets;
import oracle.HybridAdsTestOracle;
import oracle.PerfectTestOracle;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

/** One isolated CL* trial. Benchmark handles model lists, retries, resource limits and exports. */
public final class ClStarTrial {
    public static void main(String[] args) throws Exception {
        if (args.length != 8) throw new IllegalArgumentException("model cache seed partialSeed result.properties hads-executable maxTests oracleTimeoutSeconds");
        boolean cache = Boolean.parseBoolean(args[1]);
        long seed = Long.parseLong(args[2]), partialSeed = Long.parseLong(args[3]);
        int maxTests = Integer.parseInt(args[6]); long timeout = Long.parseLong(args[7]);
        CompactMealy<String, Word<String>> reference = Utils.getInstance().loadMealyMachineFromDot(new File(args[0]));
        List<String> symbols = new ArrayList<>(reference.getInputAlphabet());
        Collections.shuffle(symbols, new Random(seed));
        Alphabet<String> alphabet = Alphabets.fromList(symbols);
        StatisticSUL<String, Word<String>> mqSymbols = new SymbolCounterSUL<>("MQ", new MealySimulatorSUL<>(reference, Utils.OMEGA_SYMBOL));
        StatisticSUL<String, Word<String>> mqResets = new ResetCounterSUL<>("MQ", mqSymbols);
        StatisticSUL<String, Word<String>> eqSymbols = new SymbolCounterSUL<>("EQ", new MealySimulatorSUL<>(reference, Utils.OMEGA_SYMBOL));
        StatisticSUL<String, Word<String>> eqResets = new ResetCounterSUL<>("EQ", eqSymbols);
        SUL<String, Word<String>> mqSul = cache ? SULCache.createDAGCache(alphabet, mqResets) : mqResets;
        SUL<String, Word<String>> eqSul = cache ? SULCache.createDAGCache(alphabet, eqResets) : eqResets;
        MembershipOracle<String, Word<Word<String>>> mq = new SULOracle<>(mqSul), eq = new SULOracle<>(eqSul);
        Path executable = args[5].equals("default") ? HybridAdsTestOracle.defaultExecutable() : Path.of(args[5]);
        HybridAdsTestOracle global = new HybridAdsTestOracle(eq, executable, seed, maxTests, timeout);
        HybridAdsTestOracle partial = new HybridAdsTestOracle(eq, executable, partialSeed, maxTests, timeout);
        long[] finalEq = new long[3];
        EquivalenceOracle.MealyEquivalenceOracle<String, Word<String>> accounting = (hypothesis, inputs) -> {
            long s = counter(eqSymbols), r = counter(eqResets), t = global.getTests();
            DefaultQuery<String, Word<Word<String>>> ce = global.findCounterExample(hypothesis, inputs);
            if (ce == null) { finalEq[0] = counter(eqSymbols) - s; finalEq[1] = counter(eqResets) - r; finalEq[2] = global.getTests() - t; }
            return ce;
        };
        MealyLearnInParts learner = new MealyLearnInParts(alphabet, mq, accounting, partial, Logger.getLogger("clstar"));
        learner.setExecutionCounters(() -> counter(mqSymbols), () -> counter(mqResets));
        long start = System.nanoTime();
        CompactMealy<String, Word<String>> learned = learner.run(eqSymbols, null);
        long learningNanos = System.nanoTime() - start;
        long validationStart = System.nanoTime();
        boolean equivalent = learned != null && new PerfectTestOracle(reference).findCounterExample(learned, alphabet) == null;
        Properties raw = new Properties();
        raw.setProperty("validationNanos", "" + (System.nanoTime() - validationStart));
        raw.setProperty("learningNanosIncludingFinalEq", "" + learningNanos);
        raw.setProperty("size", "" + reference.size()); raw.setProperty("learned", "" + equivalent);
        raw.setProperty("learnInputs", "" + counter(mqSymbols)); raw.setProperty("learnResets", "" + counter(mqResets));
        raw.setProperty("minCeInputs", "" + learner.getActualMinCeSymbols()); raw.setProperty("minCeResets", "" + learner.getActualMinCeResets());
        raw.setProperty("logicalMinCeInputs", "" + learner.getMinCeSymbols().getCount());
        raw.setProperty("logicalMinCeResets", "" + learner.getMinCeResets().getCount());
        raw.setProperty("testInputs", "" + (counter(eqSymbols) - finalEq[0])); raw.setProperty("testResets", "" + (counter(eqResets) - finalEq[1]));
        raw.setProperty("testInputsIncludingFinalEq", "" + counter(eqSymbols)); raw.setProperty("testResetsIncludingFinalEq", "" + counter(eqResets));
        raw.setProperty("excludedFinalEqInputs", "" + finalEq[0]); raw.setProperty("excludedFinalEqResets", "" + finalEq[1]);
        raw.setProperty("excludedFinalEqTests", "" + finalEq[2]); raw.setProperty("finalEqExcluded", "true");
        raw.setProperty("components", "" + learner.getSigmaFamily().size()); raw.setProperty("rounds", "" + learner.getRound_counter().getCount());
        raw.setProperty("globalCalls", "" + global.getCalls()); raw.setProperty("partialCalls", "" + partial.getCalls());
        raw.setProperty("globalTests", "" + global.getTests()); raw.setProperty("partialTests", "" + partial.getTests());
        raw.setProperty("globalSeeds", global.getSeeds().toString()); raw.setProperty("partialSeeds", partial.getSeeds().toString());
        raw.setProperty("cache", "" + cache); raw.setProperty("referencePrecheck", "false");
        raw.setProperty("hadsExecutable", executable.toAbsolutePath().toString());
        raw.setProperty("hadsMaxTests", "" + maxTests); raw.setProperty("hadsTimeoutSeconds", "" + timeout);
        try (Writer writer = Files.newBufferedWriter(Path.of(args[4]))) { raw.store(writer, "CL* raw counters, after cache if enabled"); }
    }
    private static long counter(StatisticSUL<?, ?> sul) {
        return Long.parseLong(Utils.ExtractValue(sul.getStatisticalData().getSummary()));
    }
}
