package oracle;

import java.util.ArrayList;
import java.util.List;

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
        return reverse(access);
    }

    private List<V> reverse(List<V> lst) {
        List<V> reversedList = new ArrayList<>();
        for (int i = lst.size() - 1; i >= 0; i--) {
            reversedList.add(lst.get(i));
        }
        return reversedList;
    }

    public AccessNode<V> getRoot() {
        return root;
    }
}
