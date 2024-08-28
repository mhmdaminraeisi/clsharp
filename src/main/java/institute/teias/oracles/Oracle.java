package institute.teias.oracles;

import de.learnlib.driver.simulator.MealySimulatorSUL;
import de.learnlib.sul.SUL;
import net.automatalib.automaton.transducer.CompactMealy;

public abstract class Oracle<I, O> {
    protected final CompactMealy<I, O> reference;
    protected final SUL<I, O> sul;
    public abstract int getSymbolsCount();
    public abstract int getResetsCount();

    public Oracle(CompactMealy<I, O> reference) {
        this.reference = reference;
        this.sul = new MealySimulatorSUL<>(reference);
    }
}
