package institute.teias.ads;

public interface IAds<I, O> {
    I nextInput(O prevOutput);
    double identificationPower();
    void resetToRoot();
}
