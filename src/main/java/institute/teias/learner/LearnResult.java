package institute.teias.learner;

public record LearnResult(
        int learnInputs,
        int learnResets,
        int testInputs,
        int testResets,
        int rounds,
        boolean learned
) {}
