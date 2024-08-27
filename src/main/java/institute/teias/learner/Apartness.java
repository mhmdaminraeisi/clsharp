package institute.teias.learner;

import institute.teias.obsTree.Node;
import institute.teias.obsTree.ObservationForest;
import institute.teias.obsTree.ObservationTree;
import institute.teias.utils.Pair;
import net.automatalib.automaton.transducer.CompactMealy;

import java.util.*;

public class Apartness {

    public static <I, O> List<I> computeWitnessInForest(
            Node<I, O> first, Node<I, O> second,
            ObservationForest<I, O> forest
    ) {
        Node<I, O> firstDestination = showsStatesAreApart(first, second);
        if (firstDestination == null) return null;
        return forest.getTransferSequence(first, firstDestination);
    }

    public static <I, O> List<I> computeWitness(
            Node<I, O> first, Node<I, O> second,
            ObservationTree<I, O> firstTree
    ) {
        Node<I, O> firstDestination = showsStatesAreApart(first, second);
        if (firstDestination == null) return null;
        return firstTree.getTransferSequence(first, firstDestination);
    }

    public static <I, O> boolean statesAreApart(Node<I, O> first, Node<I, O> second) {
        return showsStatesAreApart(first, second) != null;
    }

    private static <I, O> Node<I, O> showsStatesAreApart(Node<I, O> first, Node<I, O> second) {
        Queue<Pair<Node<I, O>, Node<I, O>>> pairs = new LinkedList<>();
        pairs.add(new Pair<>(first, second));

        while (!pairs.isEmpty()) {
            Pair<Node<I, O>, Node<I, O>> topPair = pairs.poll();
            Node<I, O> firstNode = topPair.first();
            Node<I, O> secondNode = topPair.second();

            for (I input : firstNode.getSuccessors().keySet()) {
                O firstOutput = firstNode.getOutput(input);
                O secondOutput = secondNode.getOutput(input);
                if (firstOutput != null && secondOutput != null) {
                    if (!firstOutput.equals(secondOutput)) {
                        return firstNode.getSuccessor(input);
                    }
                    pairs.add(new Pair<>(firstNode.getSuccessor(input), secondNode.getSuccessor(input)));
                }
            }
        }

        return null;
    }

    public static <I, O> List<I> computeWitnessInForestAndHypothesis(
            ObservationForest<I, O> forest,
            CompactMealy<I, O> hypothesis
    ) {
        for (Node<I, O> root : forest.getRoots().values()) {
            Node<I, O> destination = showsStatesAreApartInTreeAndHypothesisWithInitRoot(root, hypothesis);
            if (destination != null) {
                return forest.getAccessSequence(destination);
            }
        }
        return null;
    }

    public static <I, O> List<I> computeWitnessInTreeAndHypothesis(
            ObservationTree<I, O> tree,
            CompactMealy<I, O> hypothesis
    ) {
        Node<I, O> treeDestination = showsStatesAreApartInTreeAndHypothesisWithInitRoot(tree.getRoot(), hypothesis);
        if (treeDestination == null) return null;
        return tree.getAccessSequence(treeDestination);
    }

    private static <I, O> Node<I, O> showsStatesAreApartInTreeAndHypothesisWithInitRoot(
            Node<I, O> initRoot,
            CompactMealy<I, O> hypothesis
    ) {
        Queue<Pair<Node<I, O>, Integer>> pairs = new LinkedList<>();
        pairs.add(new Pair<>(initRoot, hypothesis.getInitialState()));

        while (!pairs.isEmpty()) {
            Pair<Node<I, O>, Integer> topPair = pairs.poll();
            Node<I, O> treeNode = topPair.first();
            Integer state = topPair.second();

            for (I input : treeNode.getSuccessors().keySet()) {
                O treeOutput = treeNode.getOutput(input);
                O hypothesisOutput = hypothesis.getOutput(state, input);
                if (treeOutput != null && hypothesisOutput != null) {
                    if (!treeOutput.equals(hypothesisOutput)) {
                        return treeNode.getSuccessor(input);
                    }
                    pairs.add(new Pair<>(treeNode.getSuccessor(input), hypothesis.getSuccessor(state, input)));
                }
            }
        }
        return null;
    }
}
