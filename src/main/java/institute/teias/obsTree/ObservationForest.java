package institute.teias.obsTree;

import lombok.Getter;
import lombok.NonNull;

import java.util.*;
import java.util.stream.Collectors;

@Getter
public class ObservationForest<I, O> extends BaseObservation<I, O> {
    private final HashMap<I, ObservationTree<I, O>> trees = new HashMap<>();

    public ObservationForest(HashSet<I> initialAlphabet) {
        super(initialAlphabet);
        for (I input : initialAlphabet) {
            this.trees.put(input, new ObservationTree<>(initialAlphabet));
        }
    }

    public Collection<Node<I, O>> getRoots() {
        return this.trees.values().stream().map(ObservationTree::getRoot).collect(Collectors.toSet());
    }

    public Node<I, O> getRoot(List<I> inputs) {
        if (inputs.isEmpty()) return null;
        return this.trees.get(inputs.getFirst()).getRoot();
    }

    public void mergeWithAnotherForest(ObservationForest<I, O> anotherForest) {
        this.trees.putAll(anotherForest.getTrees());
    }

    @Override
    public void insertObservation(List<I> inputs, List<O> outputs) {
        if (inputs.isEmpty()) return;
        ObservationTree<I, O> tree = this.trees.get(inputs.getFirst());
        tree.insertObservation(inputs, outputs);
    }

    @Override
    public List<I> getAccessSequence(@NonNull Node<I, O> state) {
        ObservationTree<I, O> tree = state.getObsTree();
        return tree.getAccessSequence(state);
    }

    @Override
    public List<O> getObservation(List<I> inputs) {
        if (inputs.isEmpty()) return new ArrayList<>();
        ObservationTree<I, O> tree = this.trees.get(inputs.getFirst());
        return tree.getObservation(inputs);
    }

    @Override
    public Node<I, O> getSuccessor(List<I> inputs) {
        if (inputs.isEmpty()) return null;
        ObservationTree<I, O> tree = this.trees.get(inputs.getFirst());
        return tree.getSuccessor(inputs);
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<I, ObservationTree<I, O>> tree : this.trees.entrySet()) {
            result.append("tree ").append(tree.getKey()).append(": \n");
            result.append(tree.getValue().toString());
            result.append("\n");
        }
        return result.toString();
    }
}
