package institute.teias.obsTree;

import institute.teias.utils.Pair;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.*;

@Getter
@RequiredArgsConstructor
public abstract class BaseObservation<I, O> {
    private final HashSet<I> alphabet;
    public abstract void insertObservation(List<I> inputs, List<O> outputs);
    public void insertObservation(Node<I, O> fromState, I input, O output) {
        this.insertObservation(fromState, Collections.singletonList(input), Collections.singletonList(output));
    }
    public void insertObservation(Node<I, O> fromState, List<I> inputs, List<O> outputs) {
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
    public List<I> getTransferSequence(Node<I, O> fromState, Node<I, O> toState) {
        List<I> transferSequence = new ArrayList<>();
        Node<I, O> currentNode = toState;
        while (currentNode != fromState) {
            if (currentNode.getParent() == null) return null;
            transferSequence.add(currentNode.getInputOutput().first());
            currentNode = currentNode.getParent();
        }
        return transferSequence.reversed();
    }
    public abstract List<I> getAccessSequence(@NonNull Node<I, O> state);
    public abstract List<O> getObservation(List<I> inputs);
    public List<O> getObservation(Node<I, O> startState, List<I> inputs) {
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
    public Node<I, O> getSuccessor(Node<I, O> state, List<I> inputs) {
        Node<I, O> currentNode = state;
        for (I input : inputs) {
            if (currentNode == null) return null;
            currentNode = currentNode.getSuccessor(input);
        }
        return currentNode;
    }
    public abstract Node<I, O> getSuccessor(List<I> inputs);
}
