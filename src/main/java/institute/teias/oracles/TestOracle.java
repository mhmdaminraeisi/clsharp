package institute.teias.oracles;

import de.learnlib.oracle.EquivalenceOracle;
import de.learnlib.oracle.equivalence.RandomWordsEQOracle;
import de.learnlib.oracle.equivalence.mealy.RandomWalkEQOracle;
import de.learnlib.oracle.membership.SULOracle;
import de.learnlib.query.DefaultQuery;
import de.learnlib.sul.SUL;
import institute.teias.utils.Pair;
import net.automatalib.alphabet.Alphabets;
import net.automatalib.automaton.transducer.CompactMealy;
import net.automatalib.automaton.transducer.MealyMachine;
import net.automatalib.word.Word;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Random;

public class TestOracle<I, O> extends Oracle<I, O> {
    private final EquivalenceOracle<MealyMachine<?, I, ?, O>, I, Word<O>> equivalenceOracle;

    public TestOracle(SUL<I, O> sul) {
        super(sul);
        // TODO
        this.equivalenceOracle = new RandomWalkEQOracle<>(
            this.resetCounterSUL,
            0.03,
            10000,
            true,
            new Random(System.currentTimeMillis())
        );
//        this.equivalenceOracle = new RandomWordsEQOracle<>(
//            new SULOracle<>(this.resetCounterSUL),
//            2,
//            20,
//            300,
//            new Random(System.currentTimeMillis())
//        );
    }

    public Pair<List<I>, List<O>> findCounterExample(CompactMealy<I, O> hypothesis, HashSet<I> alphabet) {
        @Nullable DefaultQuery<I, Word<O>> ce = this.equivalenceOracle.findCounterExample(hypothesis, Alphabets.fromCollection(alphabet));
        if (ce == null) return null;
        return new Pair<>(ce.getInput().asList(), ce.getOutput().asList());
    }
}
