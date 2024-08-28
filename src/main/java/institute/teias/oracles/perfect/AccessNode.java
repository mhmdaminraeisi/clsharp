package institute.teias.oracles.perfect;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class AccessNode<V> {
    private final V value;
    private final AccessNode<V> parent;
}
