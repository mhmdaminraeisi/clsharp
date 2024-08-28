package institute.teias.obsTree;

import institute.teias.utils.Pair;
import lombok.NonNull;

import java.util.*;

public class NormalObservationTree<I, O> extends ObservationTree<I, O> {

    public NormalObservationTree(HashSet<I> alphabet) {
        super(alphabet);
    }

    @Override
    public void insertObservation(@NonNull Node<I, O> fromState, List<I> inputs, List<O> outputs) {
        if (inputs.size() != outputs.size()) {
            throw new RuntimeException("The size of inputs and outputs are not equal.");
        }
        Node<I, O> currentNode = fromState;
        Iterator<I> inputsIterator = inputs.iterator();
        Iterator<O> outputsIterator = outputs.iterator();

        while (inputsIterator.hasNext() && outputsIterator.hasNext()) {
            I input = inputsIterator.next();
            O output = outputsIterator.next();
            currentNode = currentNode.extendAndGet(input, output);
        }
    }

    @Override
    public List<I> getTransferSequence(@NonNull Node<I, O> fromState, @NonNull Node<I, O> toState) {
        List<I> transferSequence = new ArrayList<>();
        Node<I, O> currentNode = toState;
        while (currentNode != fromState) {
            if (currentNode.getParent() == null) return null;
            transferSequence.add(currentNode.getInputOutput().first());
            currentNode = currentNode.getParent();
        }
        return transferSequence.reversed();
    }

    @Override
    public List<O> getObservation(@NonNull Node<I, O> startState, List<I> inputs) {
        List<O> observation = new ArrayList<>();
        Node<I, O> currentNode = startState;

        for (I input : inputs) {
            Pair<O, Node<I, O>> outputSuccessor = currentNode.getOutputSuccessor(input);
            if (outputSuccessor == null) return null;
            currentNode = outputSuccessor.second();
            observation.add(outputSuccessor.first());
        }

        return observation;
    }

    @Override
    public Node<I, O> getSuccessor(Node<I, O> state, List<I> inputs) {
        Node<I, O> currentNode = state;
        for (I input : inputs) {
            if (currentNode == null) return null;
            currentNode = currentNode.getSuccessor(input);
        }
        return currentNode;
    }

    @Override
    public String toString() {
        LinkedList<Node<I, O>> queue = new LinkedList<>();
        queue.add(this.getRoot());
        StringBuilder result = new StringBuilder();
        while (!queue.isEmpty()) {
            Node<I, O> node = queue.poll();
            List<I> access = this.getAccessSequence(node);
            for (I input : this.getAlphabet()) {
                Node<I, O> successor = node.getSuccessor(input);
                if (successor != null) queue.add(successor);
            }
            if (node.getInputOutput() != null) {
                result.append(node.getInputOutput().first()).append("|").append(node.getInputOutput().second());
            }
            result.append(access).append("  ").append("\n");
        }
        return result.toString();
    }
}
