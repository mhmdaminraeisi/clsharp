package institute.teias.obsTree;

import institute.teias.utils.Pair;
import lombok.Getter;
import lombok.NonNull;

import java.util.*;

@Getter
public class ObservationTree<I, O> extends BaseObservation<I, O> {
    private final Node<I, O> root = new Node<>(null, null);

    public ObservationTree(HashSet<I> alphabet) {
        super(alphabet);
    }

    @Override
    public void insertObservation(List<I> inputs, List<O> outputs) {
        this.insertObservation(this.root, inputs, outputs);
    }

    @Override
    public List<I> getAccessSequence(@NonNull Node<I, O> state) {
        return this.getTransferSequence(this.root, state);
    }

    @Override
    public List<O> getObservation(List<I> inputs) {
        return this.getObservation(this.root, inputs);
    }

    @Override
    public Node<I, O> getSuccessor(List<I> inputs) {
        return this.getSuccessor(this.root, inputs);
    }

    @Override
    public String toString() {
        LinkedList<Node<I, O>> queue = new LinkedList<>();
        queue.add(this.root);
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
