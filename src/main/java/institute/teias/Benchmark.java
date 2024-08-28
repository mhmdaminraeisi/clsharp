package institute.teias;

import institute.teias.io.FileManager;
import institute.teias.learner.LSharp;
import institute.teias.learner.LearnResult;
import institute.teias.learner.Rule2;
import institute.teias.learner.Rule3;
import institute.teias.oracles.HybridAdsTestOracle;
import institute.teias.oracles.OutputOracle;
import institute.teias.oracles.TestOracle;
import institute.teias.oracles.perfect.PerfectTestOracle;
import net.automatalib.automaton.transducer.CompactMealy;

import java.io.File;
import java.io.FileWriter;
import java.util.*;
import java.util.stream.Stream;

public class Benchmark {
    public static void main(String[] args) throws Exception {
        String dirPath = "src/main/resources/experimentModels";
        File directory = new File(dirPath);
        String[] files = directory.list();

        File csvFile = new File("results.csv");
        FileWriter fileWriter = new FileWriter(csvFile);

        if (files != null) {
            fileWriter.write("model,learned,rounds,num_states,num_inputs,learn_inputs,learn_resets,test_inputs,test_resets,algorithm,seed\n");

            List<String> sortedFiles = Arrays.stream(files).sorted((t1, t2) -> {
                try {
                    CompactMealy<String, String> m1 = FileManager.loadMealyFromDotFile(new File(dirPath + "/" + t1));
                    CompactMealy<String, String> m2 = FileManager.loadMealyFromDotFile(new File(dirPath + "/" + t2));
                    if (m1.size() > m2.size()) return 1;
                    if (m1.size() < m2.size()) return -1;
                    if (m1.getInputAlphabet().size() > m2.getInputAlphabet().size()) return 1;
                    if (m1.getInputAlphabet().size() < m2.getInputAlphabet().size()) return -1;
                    return t1.compareTo(t2);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).toList();

            String[] algorithms = {"LSharp_Nothing_SepSeq", "LSharp_Ads_Ads"};
            for (int j = 0; j < algorithms.length; j++) {
                Rule2 rule2 = j == 0 ? Rule2.Nothing : Rule2.Ads;
                Rule3 rule3 = j == 0 ? Rule3.SepSeq : Rule3.Ads;

                for (String fileName : sortedFiles) {
                    for (int i = 0; i < 100; i++) {
                        System.out.println("Running algorithm " + algorithms[j] + " round " + i + " file " + fileName);

                        CompactMealy<String, String> targetMealy = FileManager.loadMealyFromDotFile(new File(dirPath + "/" + fileName));
                        OutputOracle<String, String> outputOracle = new OutputOracle<>(targetMealy);
                        TestOracle<String, String> perfectOracle = new PerfectTestOracle<>(targetMealy);
                        int seed = (new Random()).nextInt();
                        TestOracle<String, String> testOracle = new HybridAdsTestOracle<>(targetMealy, seed);
                        HashSet<String> alphabet = new HashSet<>(targetMealy.getInputAlphabet());

                        LSharp<String, String> lSharp = new LSharp<>(outputOracle, testOracle, perfectOracle, alphabet, rule2, rule3);
                        LearnResult result = lSharp.learnMealy();
                        String[] stringArray = {
                                fileName,
                                String.valueOf(result.learned()),
                                String.valueOf(result.rounds()),
                                String.valueOf(targetMealy.size()),
                                String.valueOf(targetMealy.getInputAlphabet().size()),
                                String.valueOf(result.learnInputs()),
                                String.valueOf(result.learnResets()),
                                String.valueOf(result.testInputs()),
                                String.valueOf(result.testResets()),
                                algorithms[j],
                                String.valueOf(seed)
                        };

                        fileWriter.write(String.join(",", stringArray) + "\n");
                        fileWriter.flush();
                    }
                }
            }
            fileWriter.close();
        }
    }
}
