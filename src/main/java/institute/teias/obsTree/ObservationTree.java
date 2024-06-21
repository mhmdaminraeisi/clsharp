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

    public abstract void insertObservation(Node<I, O> fromState, List<I> inputs, List<O> outputs);
    public void insertOneTransition(Node<I, O> fromState, I input, O output) {
        this.insertObservation(fromState, Collections.singletonList(input), Collections.singletonList(output));
    }
    public abstract List<I> getTransferSequence(Node<I, O> fromState, Node<I, O> toState);
    public List<I> getAccessSequence(@NonNull Node<I, O> state) {
        return this.getTransferSequence(this.root, state);
    }
    public abstract List<O> getObservation(Node<I, O> startState, List<I> inputs);
    public abstract Node<I, O> getSuccessor(Node<I, O> state, List<I> inputs);
}
