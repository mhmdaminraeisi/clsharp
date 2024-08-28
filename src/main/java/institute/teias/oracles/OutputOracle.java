package institute.teias.oracles;

import de.learnlib.oracle.MembershipOracle;
import de.learnlib.oracle.membership.SULOracle;
import net.automatalib.automaton.transducer.CompactMealy;
import net.automatalib.word.Word;
import net.automatalib.word.WordBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OutputOracle<I, O> extends Oracle<I, O> {
    private final MembershipOracle<I, Word<O>> membershipOracle;
    private List<I> lastInputs = new ArrayList<>();
    private int resets = 0;
    private int inputSymbols = 0;

    public OutputOracle(CompactMealy<I, O> reference) {
        super(reference);
        this.membershipOracle = new SULOracle<>(sul);
    }

    @Override
    public int getSymbolsCount() {
        return this.inputSymbols;
    }

    @Override
    public int getResetsCount() {
        return this.resets;
    }

    public List<O> answerQuery(List<I> inputs) {
        this.resets += 1;
        this.inputSymbols += inputs.size();
        WordBuilder<I> wordBuilder = new WordBuilder<>();
        wordBuilder.addAll(inputs);
        this.lastInputs = new ArrayList<>(inputs);
        return this.membershipOracle.answerQuery(wordBuilder.toWord()).asList();
    }

    public O answerQueryWithoutReset(I input) {
        return this.answerQueryWithoutReset(Collections.singletonList(input)).getFirst();
    }

    public List<O> answerQueryWithoutReset(List<I> inputs) {
        if (lastInputs.isEmpty()) {
            this.resets += 1;
        }
        WordBuilder<I> prefixBuilder = new WordBuilder<>();
        WordBuilder<I> suffixBuilder = new WordBuilder<>();
        prefixBuilder.addAll(lastInputs);
        suffixBuilder.addAll(inputs);
        this.inputSymbols += inputs.size();
        this.lastInputs.addAll(inputs);
        return this.membershipOracle.answerQuery(prefixBuilder.toWord(), suffixBuilder.toWord()).asList();
    }
}
