package institute.teias.obsTree;

import lombok.Getter;
import lombok.NonNull;

import java.util.*;

@Getter
public class ObservationForest<I, O> extends BaseObservation<I, O> {
    private final HashMap<I, Node<I, O>> roots = new HashMap<>();

    public ObservationForest(HashSet<I> initialAlphabet) {
        super(initialAlphabet);
        for (I input : initialAlphabet) {
            ObservationTree<I, O> observationTree = new ObservationTree<>(initialAlphabet);
            this.roots.put(input, observationTree.getRoot());
        }
    }

    public Collection<Node<I, O>> getRootNodes() {
        return this.roots.values();
    }

    public Node<I, O> getRoot(List<I> inputs) {
        if (inputs.isEmpty()) return null;
        return this.roots.get(inputs.getFirst());
    }

    public void mergeWithAnotherForest(ObservationForest<I, O> anotherForest) {
        this.roots.putAll(anotherForest.getRoots());
    }

    @Override
    public void insertObservation(List<I> inputs, List<O> outputs) {
        if (inputs.isEmpty()) return;
        Node<I, O> root = this.roots.get(inputs.getFirst());
        this.insertObservation(root, inputs, outputs);
    }

    @Override
    public List<I> getAccessSequence(@NonNull Node<I, O> state) {
        Node<I, O> root = state;
        while (root.getParent() != null) {
            root = root.getParent();
        }
        return this.getTransferSequence(root, state);
    }

    @Override
    public List<O> getObservation(List<I> inputs) {
        if (inputs.isEmpty()) return new ArrayList<>();
        Node<I, O> root = this.roots.get(inputs.getFirst());
        return this.getObservation(root, inputs);
    }

    @Override
    public Node<I, O> getSuccessor(List<I> inputs) {
        if (inputs.isEmpty()) return null;
        Node<I, O> root = this.roots.get(inputs.getFirst());
        return this.getSuccessor(root, inputs);
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<I, Node<I, O>> root : this.roots.entrySet()) {
            result.append("tree ").append(root.getKey()).append(": \n");
//            result.append(root.getValue().getObsTree().toString());
            LinkedList<Node<I, O>> queue = new LinkedList<>();
            queue.add(root.getValue());
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
            result.append("\n");
        }
        return result.toString();
    }
}
