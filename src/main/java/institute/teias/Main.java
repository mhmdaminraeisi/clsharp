package institute.teias;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

import de.learnlib.driver.simulator.MealySimulatorSUL;
import de.learnlib.filter.statistic.sul.ResetCounterSUL;
import de.learnlib.filter.statistic.sul.SymbolCounterSUL;
import de.learnlib.oracle.EquivalenceOracle;
import de.learnlib.oracle.equivalence.RandomWordsEQOracle;
import de.learnlib.oracle.membership.SULOracle;
import de.learnlib.statistic.StatisticSUL;
import de.learnlib.sul.SUL;
import institute.teias.io.FileManager;
import institute.teias.learner.*;
import institute.teias.obsTree.Node;
import institute.teias.oracles.*;
import institute.teias.oracles.perfect.PerfectTestOracle;
import net.automatalib.automaton.transducer.CompactMealy;
import net.automatalib.automaton.transducer.MealyMachine;
import net.automatalib.word.Word;

public class Main {

    public static void main(String[] args) throws Exception {
//        runTests();
//        String fileName = "src/main/resources/all_FSMs/1.dot";
////        String fileName = "src/main/resources/all_FSMs/80.dot";
////        String fileName = "src/main/resources/experimentModels/BitVise.dot";
//        CompactMealy<String, String> targetMealy = FileManager.loadMealyFromDotFile(new File(fileName));
////        System.out.println(targetMealy.size());
////


        String dirPath = "src/main/resources";
        File directory = new File(dirPath);
        String[] files = directory.list();


        String fileName = dirPath + "/all_FSMs/49.dot";
        CompactMealy<String, String> targetMealy = FileManager.loadMealyFromDotFile(new File(fileName));

        System.out.println("---------------------------------------------");
        System.out.println("file is " + fileName + "  size = " + targetMealy.size());

        OutputOracle<String, String> outputOracle = new OutputOracle<>(targetMealy);
        PerfectTestOracle<String, String> perfectOracle = new PerfectTestOracle<>(targetMealy);
        TestOracle<String, String> testOracle = new HybridAdsTestOracle<>(targetMealy, new Random().nextInt());
        TestOracle<String, String> partialTestOracle = new RandomWordsTestOracle<>(targetMealy);
        HashSet<String> alphabet = new HashSet<>(targetMealy.getInputAlphabet());
        NCLSharp<String, String> nclSharp = new NCLSharp<>(outputOracle, testOracle, partialTestOracle, perfectOracle, alphabet, Rule2.Ads, Rule3.Ads, true);
        LearnResult<String, String> result = nclSharp.learnComponents();

        System.out.println("#######################################################");
        System.out.println("Total Learn Resets: " + result.learnResets());
        System.out.println("Total Learn Inputs: " + result.learnInputs());
        System.out.println("Total Test Resets: " + result.testResets());
        System.out.println("Total Test Inputs: " + result.testInputs());
        System.out.println("Min Ce Resets: " + result.minCeResets());
        System.out.println("Min Ce Inputs: " + result.minCeInputs());


//        for (int i = 0; i < 1; i++) {
//            System.out.println("Running repeat " + i);
//            System.out.println(targetMealy.size());
//            OutputOracle<String, String> outputOracle = new OutputOracle<>(targetMealy);
//            PerfectTestOracle<String, String> perfectTestOracle = new PerfectTestOracle<>(targetMealy);
//            TestOracle<String, String> testOracle = new HybridAdsTestOracle<>(targetMealy, new Random().nextInt());
//            HashSet<String> alphabet = new HashSet<>(targetMealy.getInputAlphabet());
//            LSharp<String, String> clSharp = new LSharp<>(outputOracle, testOracle, perfectTestOracle, alphabet, Rule2.Ads, Rule3.Ads, true);
//            LearnResult<String, String> result = clSharp.learnMealy();
//            System.out.println(result.testResets() + "  " + result.testInputs() + "  " + result.learned());
////            OutputOracle<String, String> outputOracle = new OutputOracle<>(targetMealy);
////            PerfectTestOracle<String, String> perfectTestOracle = new PerfectTestOracle<>(targetMealy);
////            TestOracle<String, String> testOracle = new RandomWalkTestOracle<>(targetMealy);
////            HashSet<String> alphabet = new HashSet<>(targetMealy.getInputAlphabet());
////            CLSharp<String, String> clSharp = new CLSharp<>(outputOracle, testOracle, perfectTestOracle, alphabet, Rule2.Ads, Rule3.Ads, false);
////            LearnResult<String, String> result = clSharp.learnComponents();
////            System.out.println(result.learnInputs() + "  " + result.learnResets());
//            System.out.println("---------------------------------------");
//        }
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
//            int count = 1;
            for (String file : sortedFiles) {
//                if (count++ < 22) continue;
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

//    public static void runTests() throws Exception {
//        File csvFile = new File("results.csv");
//        FileWriter fileWriter = new FileWriter(csvFile);
//
//        fileWriter.write(",TestResets, , , , TestSymbols, , , , LearnResets, , , , LearnSymbols, , , , minCEResets, , , , minCESymbols, , , , algResets, , , , algSymbols, , , \n");
//        fileWriter.write("file,l-sharp,nclsharp,nim-nclsharp,cnclsharp,l-sharp,nclsharp,nim-nclsharp,cnclsharp,l-sharp,nclsharp,nim-nclsharp,cnclsharp,l-sharp,nclsharp,nim-nclsharp,cnclsharp,l-sharp,nclsharp,nim-nclsharp,cnclsharp,l-sharp,nclsharp,nim-nclsharp,cnclsharp,l-sharp,nclsharp,nim-nclsharp,cnclsharp,l-sharp,nclsharp,nim-nclsharp,cnclsharp\n");
//        for (int j = 1; j < 197; j++) {
//            if (j == 95) continue;
//            String fileName = "src/main/resources/all_FSMs/"+j+".dot";
//            for (int i = 0; i < 1; i++) {
//                System.out.println("\n\n\nFile " + j);
//                CompactMealy<String, String> targetMealy = FileManager.loadMealyFromDotFile(new File(fileName));
//                OutputOracle<String, String> outputOracle2 = new OutputOracle<>(targetMealy);
//                TestOracle<String, String> testOracle2 = new PerfectTestOracle<>(targetMealy);
//                TestOracle<String, String> perfectOralce = new PerfectTestOracle<>(targetMealy);
//                HashSet<String> alphabet2 = new HashSet<>(targetMealy.getInputAlphabet());
//
//                LSharp<String, String> lSharp = new LSharp<>(outputOracle2, testOracle2, perfectOralce, alphabet2, Rule2.Ads, Rule3.Ads);
//                LearnResult lResult = lSharp.learnMealy();
//                System.out.println("\nLSharp");
//                int lshTR = testOracle2.getResetsCount();
//                int lshTS = testOracle2.getSymbolsCount();
//                int lshLR = outputOracle2.getResetsCount();
//                int lshLS = outputOracle2.getSymbolsCount();
////                System.out.println("Test " + testOracle2.getResetsCount());
////                System.out.println("Test " + testOracle2.getSymbolsCount());
////                System.out.println("Learn " + outputOracle2.getResetsCount());
////                System.out.println("Learn " + outputOracle2.getSymbolsCount());
//
//
//
//                OutputOracle<String, String> outputOracle3 = new OutputOracle<>(targetMealy);
//                TestOracle<String, String> testOracle3 = new PerfectTestOracle<>(targetMealy);
//
//                HashSet<String> alphabet3 = new HashSet<>(targetMealy.getInputAlphabet());
//                CNCLSharp<String, String> cnclSharp = new CNCLSharp<>(outputOracle3, testOracle3, alphabet3);
//                List<CompactMealy<String, String>> fsm3 = cnclSharp.learnComponents();
//
//                System.out.println("\nNCLSharp");
//
//
//                int clTR = testOracle3.getResetsCount();
//                int clTS = testOracle3.getSymbolsCount();
//                int clLR = outputOracle3.getResetsCount();
//                int clLS = outputOracle3.getSymbolsCount();
//                int clmR = cnclSharp.getMinCEResets();
//                int clmS = cnclSharp.getMinCESymbols();
//                int clalR = clLR - clmR;
//                int clalS = clLS - clmS;
//
////                System.out.println("Test " + testOracle3.getResetsCount());
////                System.out.println("Test " + testOracle3.getSymbolsCount());
////                System.out.println("Learn " + outputOracle3.getResetsCount());
////                System.out.println("Learn " + outputOracle3.getSymbolsCount());
////                System.out.println("Learn min ce resets: " + cnclSharp.getMinCEResets());
////                System.out.println("Learn min ce symbols: " + cnclSharp.getMinCESymbols());
//
//
//
//                OutputOracle<String, String> outputOracle4 = new OutputOracle<>(targetMealy);
//                TestOracle<String, String> testOracle4 = new PerfectTestOracle<>(targetMealy);
//
//                HashSet<String> alphabet4 = new HashSet<>(targetMealy.getInputAlphabet());
//                NimNCLSharp<String, String> nimNCLSharp = new NimNCLSharp<>(outputOracle4, testOracle4, alphabet4);
//                List<CompactMealy<String, String>> fsm4 = nimNCLSharp.learnComponents();
//
//                System.out.println("\nNimNCLSharp");
//
//
//                int nimTR = testOracle4.getResetsCount();
//                int nimLR = outputOracle4.getResetsCount();
//                int nimTS = testOracle4.getSymbolsCount();
//                int nimLS = outputOracle4.getSymbolsCount();
//                int nimmR = nimNCLSharp.getMinCEResets();
//                int nimmS = nimNCLSharp.getMinCESymbols();
//                int nimalR = nimLR - nimmR;
//                int nimalS = nimLS - nimmS;
//
////                System.out.println("Test " + testOracle4.getResetsCount());
////                System.out.println("Test " + testOracle4.getSymbolsCount());
////                System.out.println("Learn " + outputOracle4.getResetsCount());
////                System.out.println("Learn " + outputOracle4.getSymbolsCount());
////                System.out.println("Learn min ce resets: " + nimNCLSharp.getMinCEResets());
////                System.out.println("Learn min ce symbols: " + nimNCLSharp.getMinCESymbols());
//
//
//                OutputOracle<String, String> outputOracle = new OutputOracle<>(targetMealy);
//                TestOracle<String, String> testOracle = new PerfectTestOracle<>(targetMealy);
//
//                HashSet<String> alphabet = new HashSet<>(targetMealy.getInputAlphabet());
//                NCLSharp<String, String> nclSharp = new NCLSharp<>(outputOracle, testOracle, alphabet);
//                List<CompactMealy<String, String>> fsm = nclSharp.learnComponents();
//                System.out.println("\nCNCLSharp:");
//
//
//                int cclTR = testOracle.getResetsCount();
//                int cclTS = testOracle.getSymbolsCount();
//                int cclLR = outputOracle.getResetsCount();
//                int cclLS = outputOracle.getSymbolsCount();
//                int cclmR = nclSharp.getMinCEResets();
//                int cclmS = nclSharp.getMinCESymbols();
//                int cclalR = cclLR - cclmR;
//                int cclalS = cclLS - cclmS;
////
////                System.out.println("Test " + testOracle.getResetsCount());
////                System.out.println("Test " + testOracle.getSymbolsCount());
////                System.out.println("Learn " + outputOracle.getResetsCount());
////                System.out.println("Learn " + outputOracle.getSymbolsCount());
////                System.out.println("Learn min ce resets: " + nclSharp.getMinCEResets());
////                System.out.println("Learn min ce symbols: " + nclSharp.getMinCESymbols());
//
//                int[] intArr = {j, lshTR, clTR, nimTR, cclTR, lshTS, clTS, nimTS, cclTS, lshLR, clLR, nimLR, cclLR, lshLS, clLS, nimLS, cclLS, 0, clmR, nimmR, cclmR, 0, clmS, nimmS, cclmS, 0, clalR, nimalR, cclalR, 0, clalS, nimalS, cclalS};
//                String[] stringArray = new String[intArr.length];
//                for (int ii = 0; ii < intArr.length; ii++) stringArray[ii] = String.valueOf(intArr[ii]);
//
//                fileWriter.write(String.join(",", stringArray) + "\n");
//                fileWriter.flush();
////        for (CompactMealy<String, String> mealy : fsm) {
////            StringBuilder name = new StringBuilder();
////            for (String input : mealy.getInputAlphabet()) {
////                name.append(input).append("_");
////            }
////            FileManager.saveMealyToDotFile(mealy, new File("src/main/resources/test_results/result_"+args[0]+"_"+name+".dot"));
////        }
//            }
//        }
//        fileWriter.close();
//    }
}
