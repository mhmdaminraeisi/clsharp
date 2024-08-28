package institute.teias.obsTree;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;

@Getter
@RequiredArgsConstructor
public abstract class ObservationTree<I, O> {
    private final Node<I, O> root = new Node<>(null, null);
    private final HashSet<I> alphabet;
    public void insertObservation(List<I> inputs, List<O> outputs) {
        this.insertObservation(this.root, inputs, outputs);
    }
    public void insertObservation(Node<I, O> fromState, I input, O output) {
        this.insertObservation(fromState, Collections.singletonList(input), Collections.singletonList(output));
    }
    public abstract void insertObservation(Node<I, O> fromState, List<I> inputs, List<O> outputs);
    public abstract List<I> getTransferSequence(Node<I, O> fromState, Node<I, O> toState);
    public List<I> getAccessSequence(@NonNull Node<I, O> state) {
        return this.getTransferSequence(this.root, state);
    }
    public List<O> getObservation(List<I> inputs) {
        return this.getObservation(this.root, inputs);
    }
    public abstract List<O> getObservation(Node<I, O> startState, List<I> inputs);
    public abstract Node<I, O> getSuccessor(Node<I, O> state, List<I> inputs);
    public Node<I, O> getSuccessor(List<I> inputs) {
        return this.getSuccessor(this.root, inputs);
    }
}
