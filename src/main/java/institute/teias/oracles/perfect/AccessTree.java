package institute.teias.oracles.perfect;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class AccessTree<V> {
    private final AccessNode<V> root;

    public AccessTree() {
        this.root = new AccessNode<>(null, null);
    }

    public AccessNode<V> addNode(V value, AccessNode<V> parent) {
        return new AccessNode<>(value, parent);
    }

    public List<V> getAccessSequence(AccessNode<V> node) {
        List<V> access = new ArrayList<>();
        AccessNode<V> currentNode = node;
        while (currentNode.getParent() != null) {
            access.add(currentNode.getValue());
            currentNode = currentNode.getParent();
        }
        return access.reversed();
    }
}
