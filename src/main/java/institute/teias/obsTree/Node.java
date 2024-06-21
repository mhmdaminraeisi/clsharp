package institute.teias.obsTree;

import institute.teias.utils.Pair;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.UUID;

@RequiredArgsConstructor
public class Node<I, O> {
    private final UUID id = UUID.randomUUID();
    @Getter private final Node<I, O> parent;
    @Getter private final Pair<I, O> inputOutput;
    private final HashMap<I, Pair<O, Node<I, O>>> successors = new HashMap<>();

    public Pair<O, Node<I, O>> getOutputSuccessor(I input) {
        return successors.get(input);
    }

    public O getOutput(I input) {
        if (!successors.containsKey(input)) return null;
        return successors.get(input).first();
    }

    public Node<I, O> getSuccessor(I input) {
        if (!successors.containsKey(input)) return null;
        return successors.get(input).second();
    }

    public Node<I, O> extendAndGet(I input, O output) {
        if (successors.containsKey(input)) {
            O o = this.getOutput(input);
            if (!o.equals(output)) {
                throw new RuntimeException("The output is not consistent with tree.");
            }
            return this.getSuccessor(input);
        }
        Node<I, O> successor = new Node<>(this, new Pair<>(input, output));
        this.successors.put(input, new Pair<>(output, successor));
        return successor;
    }
}
