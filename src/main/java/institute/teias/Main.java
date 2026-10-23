package institute.teias;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import institute.teias.io.FileManager;
import institute.teias.learner.*;
import institute.teias.oracles.*;
import institute.teias.oracles.perfect.PerfectTestOracle;
import net.automatalib.automaton.transducer.CompactMealy;

public class Main {

    public static void main(String[] args) throws Exception {
        String dirPath = "src/main/resources";
        String fileName = dirPath + "/all_FSMs/49.dot";
        CompactMealy<String, String> targetMealy = FileManager.loadMealyFromDotFile(new File(fileName));

        System.out.println("file is " + fileName + "  size = " + targetMealy.size());

        OutputOracle<String, String> outputOracle = new OutputOracle<>(targetMealy);
        PerfectTestOracle<String, String> perfectOracle = new PerfectTestOracle<>(targetMealy);
        TestOracle<String, String> testOracle = new HybridAdsTestOracle<>(targetMealy, new Random().nextInt());
        TestOracle<String, String> partialTestOracle = new RandomWordsTestOracle<>(targetMealy);
        HashSet<String> alphabet = new HashSet<>(targetMealy.getInputAlphabet());
        NCLSharp<String, String> nclSharp = new NCLSharp<>(outputOracle, testOracle, partialTestOracle, perfectOracle, alphabet, Rule2.Ads, Rule3.Ads, true);
        LearnResult<String, String> result = nclSharp.learnComponents();

        System.out.println("Total Learn Resets: " + result.learnResets());
        System.out.println("Total Learn Inputs: " + result.learnInputs());
        System.out.println("Total Test Resets: " + result.testResets());
        System.out.println("Total Test Inputs: " + result.testInputs());
        System.out.println("Min Ce Resets: " + result.minCeResets());
        System.out.println("Min Ce Inputs: " + result.minCeInputs());
    }

    public static void runTests() throws Exception {
        String dirPath = "src/main/resources/all_FSMs";
        File directory = new File(dirPath);
        String[] files = directory.list();

        List<String> sortedFiles = Arrays.stream(files).sorted((t1, t2) -> {
            Integer t1Int = Integer.parseInt(t1.split("\\.")[0]);
            Integer t2Int = Integer.parseInt(t2.split("\\.")[0]);
            return t1Int.compareTo(t2Int);
        }).toList();

        try (FileWriter fw = new FileWriter("check.txt", true)) {
            for (String file : sortedFiles) {
                for (int i = 1; i < 10; i++) {
                    String fileName = dirPath + "/" + file;
                    CompactMealy<String, String> targetMealy = FileManager.loadMealyFromDotFile(new File(fileName));
                    OutputOracle<String, String> outputOracle = new OutputOracle<>(targetMealy);
                    PerfectTestOracle<String, String> perfectOracle = new PerfectTestOracle<>(targetMealy);
                    TestOracle<String, String> testOracle = new RandomWalkTestOracle<>(targetMealy);
                    HashSet<String> alphabet = new HashSet<>(targetMealy.getInputAlphabet());
                    CLSharp<String, String> clSharp = new CLSharp<>(outputOracle, testOracle, perfectOracle, alphabet, Rule2.Ads, Rule3.Ads, true);
                    long startTime = System.currentTimeMillis();
                    LearnResult<String, String> result = clSharp.learnComponents();
                    long endTime = System.currentTimeMillis();
                    long time = endTime - startTime;
                    System.out.println(file + " : " + i + " components: " + result.components() + "  size: " + targetMealy.size() + "  time: " + time);
                }
                fw.write("Done file " + file + "\n");
                fw.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
