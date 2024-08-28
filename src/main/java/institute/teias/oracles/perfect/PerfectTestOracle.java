package institute.teias.oracles.perfect;

import institute.teias.oracles.TestOracle;
import institute.teias.utils.Pair;
import net.automatalib.automaton.transducer.CompactMealy;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

public class PerfectTestOracle<I, O> extends TestOracle<I, O> {
    public PerfectTestOracle(CompactMealy<I, O> reference) {
        super(reference);
    }

    @Override
    public Pair<List<I>, List<O>> findCounterExample(CompactMealy<I, O> hypothesis) {
        HashSet<Pair<Integer, Integer>> visitedPairs = new HashSet<>();
        LinkedList<Pair<AccessNode<I>, Pair<Integer, Integer>>> workList = new LinkedList<>();
        AccessTree<I> accessTree = new AccessTree<>();

        workList.add(new Pair<>(accessTree.getRoot(), new Pair<>(reference.getInitialState(), hypothesis.getInitialState())));

        while (!workList.isEmpty()) {
            Pair<AccessNode<I>, Pair<Integer, Integer>> top = workList.poll();
            Integer refState = top.second().first();
            Integer hypState = top.second().second();
            AccessNode<I> accessNode = top.first();

            for (I input : reference.getInputAlphabet()) {
                Integer refDest = reference.getSuccessor(refState, input);
                O refOut = reference.getOutput(refState, input);
                Integer hypDest = hypothesis.getSuccessor(hypState, input);
                O hypOut = hypothesis.getOutput(hypState, input);

                if (refOut == null || hypOut == null || refDest == null || hypDest == null) {
                    throw new RuntimeException("Outputs and Destinations must not be null.");
                }
                if (!refOut.equals(hypOut)) {
                    List<I> inputs = accessTree.getAccessSequence(accessNode);
                    inputs.add(input);
                    List<O> outputs = reference.computeOutput(inputs).asList();
                    return new Pair<>(inputs, outputs);
                }
                if (refDest.equals(refState) && hypDest.equals(hypState)) continue;

                Pair<Integer, Integer> newPair = new Pair<>(refDest, hypDest);
                if (!visitedPairs.contains(newPair)) {
                    AccessNode<I> newNode = accessTree.addNode(input, accessNode);
                    workList.add(new Pair<>(newNode, newPair));
                    visitedPairs.add(newPair);
                }
            }
        }
        return null;
    }

    @Override
    public int getSymbolsCount() {
        return 0;
    }

    @Override
    public int getResetsCount() {
        return 0;
    }
}
