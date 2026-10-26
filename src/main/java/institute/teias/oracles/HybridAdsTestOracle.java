package institute.teias.oracles;

import institute.teias.utils.Pair;
import net.automatalib.automaton.transducer.CompactMealy;
import net.automatalib.serialization.dot.GraphDOT;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class HybridAdsTestOracle<I, O> extends TestOracle<I, O> {
    private int resets = 0;
    private int inputSymbols = 0;

    private final ProcessBuilder pb;

    // We buffer queries, in order to allow for parallel membership queries.
    private final int BufferSize = 5;
    private final List<List<I>> buffer = new ArrayList<>(BufferSize);

    private static final Set<Process> ACTIVE = java.util.concurrent.ConcurrentHashMap.newKeySet();
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> ACTIVE.forEach(Process::destroyForcibly)));
    }
    private Process process;
    private Writer processInput;
    private BufferedReader processOutput;

    public HybridAdsTestOracle(CompactMealy<I, O> reference, int seed) {
        super(reference);
        Path currentPath = Paths.get("").toAbsolutePath();
        pb = new ProcessBuilder(currentPath + "/hybrid-ads/build/main",
                "-p", "buggy", "-m", "random", "-k", "10", "-s", "hads", "-r", "10", "-x", String.valueOf(seed)).redirectError(ProcessBuilder.Redirect.INHERIT);
    }

    private void setupProcess() throws IOException {
        process = pb.start();
        ACTIVE.add(process);
        processInput = new OutputStreamWriter(process.getOutputStream());
        processOutput = new BufferedReader(new InputStreamReader(process.getInputStream()));
    }

    private void closeAll() {
        if (process != null) {
            process.destroyForcibly();
            ACTIVE.remove(process);
        }
        try { if (processInput != null) processInput.close(); } catch (IOException ignored) { }
        try { if (processOutput != null) processOutput.close(); } catch (IOException ignored) { }
        buffer.clear();
    }

    @Override
    public Pair<List<I>, List<O>> findCounterExample(CompactMealy<I, O> hypothesis, HashSet<I> projAlphabet) {
        buffer.clear();
        try {
            setupProcess();
            GraphDOT.write(hypothesis, hypothesis.getInputAlphabet(), processInput);
            processInput.flush();
            String line;
            while ((line = processOutput.readLine()) != null) {
                List<I> testSuite = new ArrayList<>();
                try (Scanner scanner = new Scanner(line)) {
                    while (scanner.hasNext()) testSuite.add((I) scanner.next());
                }
                buffer.add(testSuite);
                if (buffer.size() >= BufferSize) {
                    Pair<List<I>, List<O>> ce = checkAndClearBuffer(hypothesis);
                    if (ce != null) return ce;
                }
            }
            if (process.waitFor() != 0) throw new IllegalStateException("Hybrid-ADS exited with code " + process.exitValue());
            // A finite test stream can end before the buffer reaches five queries.
            return checkAndClearBuffer(hypothesis);
        } catch (IOException e) {
            throw new IllegalStateException("Hybrid-ADS failed; check executable and attempt log", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Hybrid-ADS interrupted", e);
        } finally {
            closeAll();
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
