package institute.teias.learner;

import net.automatalib.automaton.transducer.CompactMealy;

public record LearnResult<I, O>(
        int learnInputs,
        int learnResets,
        int testInputs,
        int testResets,
        int rounds,
        int components,
        int minCeInputs,
        int minCeResets,
        boolean learned,
        CompactMealy<I, O> hypothesis,
        int maxComponentSize
) {}
