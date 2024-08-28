package institute.teias.oracles;

import institute.teias.utils.Pair;
import net.automatalib.automaton.transducer.CompactMealy;
import net.automatalib.serialization.dot.GraphDOT;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

public class HybridAdsTestOracle<I, O> extends TestOracle<I, O> {
    private int resets = 0;
    private int inputSymbols = 0;

    private final ProcessBuilder pb;

    // We buffer queries, in order to allow for parallel membership queries.
    private final int BufferSize = 1000;
    private final List<List<I>> buffer = new ArrayList<>(BufferSize);

    private Process process;
    private Writer processInput;
    private BufferedReader processOutput;

    public HybridAdsTestOracle(CompactMealy<I, O> reference, int seed) {
        super(reference);
        pb = new ProcessBuilder("/home/amin/projects/teias/java-lsharp/hybrid-ads/build/main",
                "-p", "buggy", "-m", "random", "-k", "10", "-s", "hads", "-r", "10", "-x", String.valueOf(seed));
    }

    private void setupProcess() throws IOException {
        process = pb.start();
        processInput = new OutputStreamWriter(process.getOutputStream());
        processOutput = new BufferedReader(new InputStreamReader(process.getInputStream()));
    }

    private void closeAll() throws IOException {
        processInput.close();
        processOutput.close();
        process.destroy();
        process.destroyForcibly();
    }

    @Override
    public Pair<List<I>, List<O>> findCounterExample(CompactMealy<I, O> hypothesis) {
        long startTime = System.currentTimeMillis();

        try {
            try {
                setupProcess();
            } catch (IOException e) {
                throw new RuntimeException("Unable to start the external program: " + e);
            }

            try {
                // Write the hypothesis to stdin of the external program
                GraphDOT.write(hypothesis, hypothesis.getInputAlphabet(), processInput);
                processInput.flush();

                // Read every line outputted on stdout.
                // We buffer the queries, so that a parallel membership query can be applied
                String line;
                while ((line = processOutput.readLine()) != null) {
                    // Read every string of the line, this will be a symbol of the input sequence.
                    List<I> testSuit = new ArrayList<>();
                    Scanner s = new Scanner(line);
                    while (s.hasNext()) {
                        testSuit.add((I) s.next());
                    }
                    buffer.add(testSuit);

                    // If the buffer is filled, we can perform the checks (possibly in parallel)
                    if (buffer.size() >= BufferSize) {
                        Pair<List<I>, List<O>> ce = this.checkAndClearBuffer(hypothesis);
                        if (ce != null) {
                            closeAll();
                            s.close();
                            return ce;
                        }
                    }

                    long endTime = System.currentTimeMillis();
                    if (endTime - startTime >= 20 * 60 * 1000){ // 20 minutes
                        break;
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Unable to communicate with the external program: " + e);
            }
            closeAll();

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private Pair<List<I>, List<O>> checkAndClearBuffer(CompactMealy<I, O> hypothesis) {
        Pair<List<I>, List<O>> ce = this.checkHasCE(hypothesis);
        buffer.clear();
        return ce;
    }

    private Pair<List<I>, List<O>> checkHasCE(CompactMealy<I, O> hypothesis) {
        for (List<I> word : buffer) {
            this.resets += 1;
            List<I> ceInputs = new ArrayList<>();
            List<O> ceOutputs = new ArrayList<>();
            Integer hypCurrentState = hypothesis.getInitialState();
            Integer refCurrentState = reference.getInitialState();

            for (I input : word) {
                ceInputs.add(input);
                O hypOutput = hypothesis.getOutput(hypCurrentState, input);
                hypCurrentState = hypothesis.getSuccessor(hypCurrentState ,input);

                O refOutput = reference.getOutput(refCurrentState, input);
                refCurrentState = reference.getSuccessor(refCurrentState, input);
                ceOutputs.add(refOutput);
                this.inputSymbols += 1;

                if (hypOutput == null || refOutput == null) {
                    throw new RuntimeException("Output must not be null!");
                }

                if (!hypOutput.equals(refOutput)) {
                    return new Pair<>(ceInputs, ceOutputs);
                }
            }
        }
        return null;
    }

    @Override
    public int getSymbolsCount() {
        return this.inputSymbols;
    }

    @Override
    public int getResetsCount() {
        return this.resets;
    }
}
