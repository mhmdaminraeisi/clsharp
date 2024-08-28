package institute.teias.ads;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;

@RequiredArgsConstructor
public class AdsNode<I, O> {
    @Getter private final I input;
    private final HashMap<O, AdsNode<I, O>> children;
    @Getter private final int score;

    public AdsNode<I, O> getChildNode(O lastOutput) {
        return children.get(lastOutput);
    }

    public static <I, O> AdsNode<I, O> createLeaf() {
        return new AdsNode<>(null, new HashMap<>(), 0);
    }
}
