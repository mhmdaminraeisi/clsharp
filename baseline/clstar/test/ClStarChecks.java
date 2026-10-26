import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.driver.util.MealySimulatorSUL;
import de.learnlib.filter.cache.sul.SULCache;
import de.learnlib.filter.statistic.sul.ResetCounterSUL;
import de.learnlib.oracle.membership.SULOracle;
import net.automatalib.automata.transducers.impl.compact.CompactMealy;
import net.automatalib.words.Word;
import net.automatalib.words.impl.Alphabets;
import oracle.HybridAdsTestOracle;
import java.nio.file.Paths;

/** Integration checks using the real HADS executable and LearnLib cache. */
public class ClStarChecks {
    public static void main(String[] args) {
        var alphabet = Alphabets.fromArray("a");
        CompactMealy<String, Word<String>> target = new CompactMealy<>(alphabet);
        int state = target.addInitialState();
        target.addTransition(state, "a", state, Word.fromSymbols("ok"));
        var resets = new ResetCounterSUL<String, Word<String>>("resets", new MealySimulatorSUL<>(target));
        MembershipOracle<String, Word<Word<String>>> mq = new SULOracle<>(SULCache.createDAGCache(alphabet, resets));
        var executable = Paths.get(args[0], "hybrid-ads/build/main");
        var first = new HybridAdsTestOracle(mq, executable, 42, 100, 30);
        if (first.findCounterExample(target, alphabet) != null || first.getTests() != 100)
            throw new AssertionError("Equivalent target must complete exactly 100 tests");
        String before = resets.getStatisticalData().getSummary();
        var replay = new HybridAdsTestOracle(mq, executable, 42, 100, 30);
        if (replay.findCounterExample(target, alphabet) != null)
            throw new AssertionError("Replay should remain equivalent");
        if (!before.equals(resets.getStatisticalData().getSummary()))
            throw new AssertionError("Replaying identical HADS tests must not execute target resets");
        CompactMealy<String, Word<String>> wrong = new CompactMealy<>(alphabet);
        int bad = wrong.addInitialState();
        wrong.addTransition(bad, "a", bad, Word.fromSymbols("wrong"));
        var mismatch = new HybridAdsTestOracle(mq, executable, 42, 100, 30);
        if (mismatch.findCounterExample(wrong, alphabet) == null)
            throw new AssertionError("HADS must find the deliberately incorrect output");
        try {
            new HybridAdsTestOracle(mq, Paths.get(args[0], "build/definitely-missing-hads"), 42, 100, 30)
                    .findCounterExample(target, alphabet);
            throw new AssertionError("Missing executable was silently accepted");
        } catch (IllegalStateException expected) { }
        try {
            new HybridAdsTestOracle(mq, Paths.get("/usr/bin/false"), 42, 100, 30)
                    .findCounterExample(target, alphabet);
            throw new AssertionError("Generator failure was silently accepted");
        } catch (IllegalStateException expected) { }
        // No-cache mode must execute a replay through the target again.
        var uncachedResets = new ResetCounterSUL<String, Word<String>>("uncached", new MealySimulatorSUL<>(target));
        MembershipOracle<String, Word<Word<String>>> uncached = new SULOracle<>(uncachedResets);
        new HybridAdsTestOracle(uncached, executable, 42, 100, 30).findCounterExample(target, alphabet);
        String once = uncachedResets.getStatisticalData().getSummary();
        new HybridAdsTestOracle(uncached, executable, 42, 100, 30).findCounterExample(target, alphabet);
        if (once.equals(uncachedResets.getStatisticalData().getSummary())) throw new AssertionError("No-cache replay did not execute target queries");
        System.out.println("PASS HADS generation, cache replay, no-cache replay, CE detection and explicit generator failures");
    }
}
