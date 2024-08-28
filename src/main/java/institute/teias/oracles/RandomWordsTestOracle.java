package institute.teias.oracles;

import de.learnlib.oracle.EquivalenceOracle;
import de.learnlib.oracle.equivalence.RandomWordsEQOracle;
import de.learnlib.oracle.membership.SULOracle;
import net.automatalib.automaton.transducer.CompactMealy;
import net.automatalib.automaton.transducer.MealyMachine;
import net.automatalib.word.Word;

import java.util.Random;

public class RandomWordsTestOracle<I, O> extends LearnLibTestOracle<I, O> {

    public RandomWordsTestOracle(CompactMealy<I, O> reference) {
        super(reference);
    }

    @Override
    protected EquivalenceOracle<MealyMachine<?, I, ?, O>, I, Word<O>> initializeEquivalenceOracle() {
        return new RandomWordsEQOracle<>(
            new SULOracle<>(this.resetCounterSUL),
            2,
            100,
            30000,
            new Random(System.currentTimeMillis())
        );
    }
}
