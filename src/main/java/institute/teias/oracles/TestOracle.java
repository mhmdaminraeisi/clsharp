package institute.teias.oracles;

import institute.teias.utils.Pair;
import net.automatalib.automaton.transducer.CompactMealy;

import java.util.HashSet;
import java.util.List;

public abstract class TestOracle<I, O> extends Oracle<I, O> {
    public TestOracle(CompactMealy<I, O> reference) {
        super(reference);
    }

    public Pair<List<I>, List<O>> findCounterExample(CompactMealy<I, O> hypothesis) {
        return this.findCounterExample(hypothesis, new HashSet<>(reference.getInputAlphabet()));
    }
    public abstract Pair<List<I>, List<O>> findCounterExample(CompactMealy<I, O> hypothesis, HashSet<I> projAlphabet);
}
