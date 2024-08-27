package institute.teias.obsTree;

import institute.teias.utils.Pair;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.checkerframework.checker.units.qual.N;

import java.util.*;

@Getter
public class ObservationForest<I, O> {
    private final HashMap<I, Node<I, O>> roots = new HashMap<>();

    public ObservationForest(HashSet<I> initialAlphabet) {
        for (I input : initialAlphabet) {
            roots.put(input, new Node<>(null, null));
        }
    }

    public void mergeWithAnotherForest(ObservationForest<I, O> anotherForest) {
        this.roots.putAll(anotherForest.getRoots());
    }

    public Node<I, O> getRoot(List<I> inputs) {
        if (inputs.isEmpty()) return null;
        return this.roots.get(inputs.getFirst());
    }

    public void insertObservation(List<I> inputs, List<O> outputs) {
        if (inputs.isEmpty()) return;
        this.insertObservation(this.roots.get(inputs.getFirst()), inputs, outputs);
    }

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

    public void insertOneTransition(Node<I, O> fromState, I input, O output) {
        this.insertObservation(fromState, Collections.singletonList(input), Collections.singletonList(output));
    }

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

    public List<I> getAccessSequence(@NonNull Node<I, O> state) {
        List<I> accessSequence = new ArrayList<>();
        Node<I, O> currentNode = state;
        while (currentNode.getParent() != null) {
            accessSequence.add(currentNode.getInputOutput().first());
            currentNode = currentNode.getParent();
        }
        return accessSequence.reversed();
    }

    public List<O> getObservation(List<I> inputs) {
        if (inputs.isEmpty()) return new ArrayList<>();
        return this.getObservation(this.getRoots().get(inputs.getFirst()), inputs);
    }

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

    public Node<I, O> getSuccessor(List<I> inputs) {
        if (inputs.isEmpty()) return null;
        return this.getSuccessor(this.roots.get(inputs.getFirst()), inputs);
    }

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
        StringBuilder result = new StringBuilder();
        for (Map.Entry<I, Node<I, O>> root : this.roots.entrySet()) {
            result.append("tree ").append(root.getKey()).append(": \n");
            LinkedList<Node<I, O>> queue = new LinkedList<>();
            queue.add(root.getValue());
            while (!queue.isEmpty()) {
                Node<I, O> node = queue.poll();
                List<I> access = this.getAccessSequence(node);
                for (I input : node.getSuccessors().keySet()) {
                    Node<I, O> successor = node.getSuccessor(input);
                    if (successor != null) queue.add(successor);
                }
                if (node.getInputOutput() != null) {
                    result.append(node.getInputOutput().first()).append("|").append(node.getInputOutput().second());
                }
                result.append(access).append("  ").append("\n");
            }
            result.append("\n");
        }
        return result.toString();
    }
}
