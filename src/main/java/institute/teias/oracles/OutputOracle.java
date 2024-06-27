package institute.teias.oracles;

import de.learnlib.oracle.MembershipOracle;
import de.learnlib.oracle.membership.SULOracle;
import de.learnlib.sul.SUL;
import net.automatalib.word.Word;
import net.automatalib.word.WordBuilder;

import java.util.List;

public class OutputOracle<I, O> extends Oracle<I, O> {
    private final MembershipOracle<I, Word<O>> membershipOracle;
    private final WordBuilder<I> wordBuilder = new WordBuilder<>();

    public OutputOracle(SUL<I, O> sul) {
        super(sul);
        this.membershipOracle = new SULOracle<>(this.resetCounterSUL);
    }

    public List<O> answerQuery(List<I> inputs) {
        this.wordBuilder.clear();
        this.wordBuilder.addAll(inputs);
        return this.membershipOracle.answerQuery(this.wordBuilder.toWord()).asList();
    }
}
