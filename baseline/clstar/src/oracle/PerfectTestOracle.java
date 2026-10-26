package oracle;
import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.query.DefaultQuery;
import net.automatalib.automata.transducers.MealyMachine;
import net.automatalib.automata.transducers.impl.compact.CompactMealy;
import net.automatalib.words.Word;
import net.automatalib.words.WordBuilder;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.*;

public class PerfectTestOracle implements EquivalenceOracle.MealyEquivalenceOracle<String, Word<String>> {
    private final CompactMealy<String, Word<String>> reference;

    public PerfectTestOracle(CompactMealy<String, Word<String>> reference) {
        this.reference = reference;
    }

    public int getSymbolsCount() {
        return 0;
    }

    public int getResetsCount() {
        return 0;
    }

    @Override
    public @Nullable DefaultQuery<String, Word<Word<String>>> findCounterExample(MealyMachine<?, String, ?, Word<String>> hyp, Collection<? extends String> collection) {
        CompactMealy<String, Word<String>> hypothesis = (CompactMealy<String, Word<String>>) hyp;
        HashSet<Pair<Integer, Integer>> visitedPairs = new HashSet<>();
        LinkedList<Pair<AccessNode<String>, Pair<Integer, Integer>>> workList = new LinkedList<>();
        AccessTree<String> accessTree = new AccessTree<>();

        workList.add(new Pair<>(accessTree.getRoot(), new Pair<>(reference.getInitialState(), hypothesis.getInitialState())));

        while (!workList.isEmpty()) {
            Pair<AccessNode<String>, Pair<Integer, Integer>> top = workList.poll();
            Integer refState = top.second().first();
            Integer hypState = top.second().second();
            AccessNode<String> accessNode = top.first();

            for (String input : hypothesis.getInputAlphabet()) {
                Integer refDest = reference.getSuccessor(refState, input);
                Word<String> refOut = reference.getOutput(refState, input);
                Integer hypDest = hypothesis.getSuccessor(hypState, input);
                Word<String> hypOut = hypothesis.getOutput(hypState, input);

                if (refOut == null || hypOut == null || refDest == null || hypDest == null) {
                    throw new RuntimeException("Outputs and Destinations must not be null.");
                }
                for (int i = 0; i < refOut.size(); i++) {
                    if (!refOut.getSymbol(i).equals(hypOut.getSymbol(i))) {
                        WordBuilder<String> ceInputs = new WordBuilder<>();
                        WordBuilder<Word<String>> ceOutputs = new WordBuilder<>();
                        List<String> inputs = accessTree.getAccessSequence(accessNode);
                        inputs.add(input);
                        List<Word<String>> outputs = reference.computeOutput(inputs).asList();

                        ceInputs.addAll(inputs);
                        ceOutputs.addAll(outputs);
                        return new DefaultQuery<>(ceInputs.toWord(), ceOutputs.toWord());
                    }
                }
                if (refDest.equals(refState) && hypDest.equals(hypState)) continue;

                Pair<Integer, Integer> newPair = new Pair<>(refDest, hypDest);
                if (!visitedPairs.contains(newPair)) {
                    AccessNode<String> newNode = accessTree.addNode(input, accessNode);
                    workList.add(new Pair<>(newNode, newPair));
                    visitedPairs.add(newPair);
                }
            }
        }
        return null;
    }
}
