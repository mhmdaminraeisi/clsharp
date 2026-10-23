package institute.teias;

import institute.teias.io.FileManager;
import institute.teias.learner.*;
import institute.teias.oracles.*;
import institute.teias.oracles.perfect.PerfectTestOracle;
import net.automatalib.automaton.transducer.CompactMealy;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

public class Benchmark {
    public static void main(String[] args) throws Exception {

        File csvFile = new File("test_results.csv");
        FileWriter fileWriter = new FileWriter(csvFile);

        String dirPath = "src/main/resources/all_FSMs";
        File directory = new File(dirPath);
        String[] files = directory.list();

        List<String> sortedFiles = Arrays.stream(files).sorted((t1, t2) -> {
            Integer t1Int = Integer.parseInt(t1.split("\\.")[0]);
            Integer t2Int = Integer.parseInt(t2.split("\\.")[0]);
            return t1Int.compareTo(t2Int);
        }).toList();


        fileWriter.write("file,algorithm,learned,rounds,size,components,learnSymbols,learnResets,TestSymbols,TestResets,minCeSymbols,minCeResets,RunTime,maxCompSize\n");


        String[] algs = new String[] {"nclsharp", "clsharp"};
        int count = 0;

        for (String file : sortedFiles) {
            for (String alg : algs) {
                int r = 1;
                int allCount = 0;
                while (r <= 3 && allCount < 20) {
                    String fileName = dirPath + "/" + file;
                    CompactMealy<String, String> targetMealy = FileManager.loadMealyFromDotFile(new File(fileName));

                    String algorithm = alg + "-ADS";

                    Results res = runTest(alg, targetMealy, Rule2.Ads, Rule3.Ads, true);
                    assert res != null;
                    if (!res.learned) continue;

                    System.out.println("Done " + file + "   " + algorithm + "   repeat " + r + "  components: " + res.components + "  size: " + targetMealy.size() + " TestResets: " + res.testResets + "  TestSymbols: " + res.testSymbols + "  LearnResets: " + res.learnResets + "  LearnSymbols: " + res.learnSymbols + "  Time: " + res.runTime);
                    int[] intArr = {res.rounds, targetMealy.size(), res.components, res.learnSymbols, res.learnResets, res.testSymbols, res.testResets, res.minCeSymbols, res.minCeResets, (int) res.runTime, res.maxCompSize};
                    String[] stringArray = new String[intArr.length];
                    for (int ii = 0; ii < intArr.length; ii++) stringArray[ii] = String.valueOf(intArr[ii]);

                    String row = file + "," + algorithm + "," + "true" + "," + String.join(",", stringArray) + "\n";
                    fileWriter.write(row);
                    fileWriter.flush();
                    r++;
                    allCount++;
                }
            }
        }
    }

    private static Results runTest(String alg, CompactMealy<String, String> targetMealy, Rule2 rule2, Rule3 rule3, boolean cache) throws IOException {
        OutputOracle<String, String> outputOracle = new OutputOracle<>(targetMealy);
        TestOracle<String, String> testOracle = new HybridAdsTestOracle<>(targetMealy, new Random(System.currentTimeMillis()).nextInt());
        TestOracle<String, String> partialTestOracle = new HybridAdsTestOracle<>(targetMealy, new Random(System.currentTimeMillis()).nextInt());
//        TestOracle<String, String> testOracle = new RandomWalkTestOracle<>(targetMealy);
//        TestOracle<String, String> testOracle = new RandomWordsTestOracle<>(targetMealy);
        PerfectTestOracle<String, String> perfectTestOracle = new PerfectTestOracle<>(targetMealy);
        HashSet<String> alphabet = new HashSet<>(targetMealy.getInputAlphabet());

        if (alg.equals("lsharp")) {
            LSharp<String, String> lSharp = new LSharp<>(outputOracle, testOracle, perfectTestOracle, alphabet, rule2, rule3, cache);
            long startTime = System.currentTimeMillis();
            LearnResult<String, String> result = lSharp.learnMealy();
            long endTime = System.currentTimeMillis();
            long time = endTime - startTime;
            return new Results(result.learnInputs(), result.learnResets(), result.testInputs(), result.testResets(), result.rounds(), result.components(), result.minCeInputs(), result.minCeResets(), time, result.learned(), result.maxComponentSize());
        }
        if (alg.equals("nclsharp")) {
            NCLSharp<String, String> nclSharp = new NCLSharp<>(outputOracle, testOracle, partialTestOracle, perfectTestOracle, alphabet, rule2, rule3, cache);
            long startTime = System.currentTimeMillis();
            LearnResult<String, String> result = nclSharp.learnComponents();
            long endTime = System.currentTimeMillis();
            long time = endTime - startTime;
            return new Results(result.learnInputs(), result.learnResets(), result.testInputs(), result.testResets(), result.rounds(), result.components(), result.minCeInputs(), result.minCeResets(), time, result.learned(), result.maxComponentSize());
        }
        if (alg.equals("clsharp")) {
            CLSharp<String, String> clSharp = new CLSharp<>(outputOracle, testOracle, perfectTestOracle, alphabet, rule2, rule3, cache);
            long startTime = System.currentTimeMillis();
            LearnResult<String, String> result = clSharp.learnComponents();
            long endTime = System.currentTimeMillis();
            long time = endTime - startTime;
            return new Results(result.learnInputs(), result.learnResets(), result.testInputs(), result.testResets(), result.rounds(), result.components(), result.minCeInputs(), result.minCeResets(), time, result.learned(), result.maxComponentSize());
        }
        return null;
    }

    private record Results(
        int learnSymbols,
        int learnResets,
        int testSymbols,
        int testResets,
        int rounds,
        int components,
        int minCeSymbols,
        int minCeResets,
        long runTime,
        boolean learned,
        int maxCompSize
    ) {}
}
