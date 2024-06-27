package institute.teias.oracles;

import de.learnlib.filter.statistic.sul.ResetCounterSUL;
import de.learnlib.filter.statistic.sul.SymbolCounterSUL;
import de.learnlib.statistic.StatisticSUL;
import de.learnlib.sul.SUL;

public abstract class Oracle<I, O> {
    protected final StatisticSUL<I, O> symbolCounterSUL;
    protected final StatisticSUL<I, O> resetCounterSUL;

    protected Oracle(SUL<I, O> sul) {
        this.symbolCounterSUL = new SymbolCounterSUL<>("symbolCounter", sul);
        this.resetCounterSUL = new ResetCounterSUL<>("resetCounter", this.symbolCounterSUL);
    }

    public String getSymbolsCount() {
        return this.symbolCounterSUL.getStatisticalData().getSummary();
    }

    public String getResetsCount() {
        return this.resetCounterSUL.getStatisticalData().getSummary();
    }
}
