package institute.teias.oracles;

import de.learnlib.oracle.EquivalenceOracle;
import de.learnlib.oracle.equivalence.mealy.RandomWalkEQOracle;
import net.automatalib.automaton.transducer.CompactMealy;
import net.automatalib.automaton.transducer.MealyMachine;
import net.automatalib.word.Word;

import java.util.Random;

public class RandomWalkTestOracle<I, O> extends LearnLibTestOracle<I, O> {

    public RandomWalkTestOracle(CompactMealy<I, O> reference) {
        super(reference);
    }

    @Override
    protected EquivalenceOracle<MealyMachine<?, I, ?, O>, I, Word<O>> initializeEquivalenceOracle() {
        return new RandomWalkEQOracle<>(
            this.resetCounterSUL,
            0.03,
            10000,
            true,
            new Random(System.currentTimeMillis())
        );
    }
}
