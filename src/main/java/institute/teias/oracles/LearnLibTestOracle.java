package institute.teias.oracles;

import de.learnlib.filter.statistic.sul.ResetCounterSUL;
import de.learnlib.filter.statistic.sul.SymbolCounterSUL;
import de.learnlib.oracle.EquivalenceOracle;
import de.learnlib.query.DefaultQuery;
import de.learnlib.statistic.StatisticSUL;
import institute.teias.utils.Pair;
import net.automatalib.automaton.transducer.CompactMealy;
import net.automatalib.automaton.transducer.MealyMachine;
import net.automatalib.word.Word;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;

public abstract class LearnLibTestOracle<I, O> extends TestOracle<I, O> {
    protected final EquivalenceOracle<MealyMachine<?, I, ?, O>, I, Word<O>> equivalenceOracle;
    protected final StatisticSUL<I, O> symbolCounterSUL;
    protected final StatisticSUL<I, O> resetCounterSUL;

    public LearnLibTestOracle(CompactMealy<I, O> reference) {
        super(reference);
        this.symbolCounterSUL = new SymbolCounterSUL<>("symbolCounter", sul);
        this.resetCounterSUL = new ResetCounterSUL<>("resetCounter", this.symbolCounterSUL);
        this.equivalenceOracle = this.initializeEquivalenceOracle();
    }

    protected abstract EquivalenceOracle<MealyMachine<?, I, ?, O>, I, Word<O>> initializeEquivalenceOracle();

    @Override
    public int getSymbolsCount() {
        return this.getCountNumber(this.symbolCounterSUL.getStatisticalData().getSummary());
    }

    @Override
    public int getResetsCount() {
        return this.getCountNumber(this.resetCounterSUL.getStatisticalData().getSummary());
    }

    private int getCountNumber(String summary) {
        String[] strSplit = summary.split(" ");
        String strNumber = strSplit[strSplit.length-1];
        return Integer.parseInt(strNumber);
    }

    public Pair<List<I>, List<O>> findCounterExample(CompactMealy<I, O> hypothesis) {
        @Nullable DefaultQuery<I, Word<O>> ce = this.equivalenceOracle.findCounterExample(hypothesis, hypothesis.getInputAlphabet());
        if (ce == null) return null;
        return new Pair<>(ce.getInput().asList(), ce.getOutput().asList());
    }
}
