package oracle;

public class AccessNode<V> {
    private final V value;
    private final AccessNode<V> parent;

    public AccessNode(V value, AccessNode<V> parent) {
        this.value = value;
        this.parent = parent;
    }

    public V getValue() {
        return value;
    }

    public AccessNode<V> getParent() {
        return parent;
    }
}
