package institute.teias;

import java.io.File;
import java.io.FileWriter;
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
import institute.teias.oracles.HybridAdsTestOracle;
import institute.teias.oracles.OutputOracle;
import institute.teias.oracles.TestOracle;
import institute.teias.oracles.perfect.PerfectTestOracle;
import net.automatalib.automaton.transducer.CompactMealy;
import net.automatalib.automaton.transducer.MealyMachine;
import net.automatalib.word.Word;

public class Main {

    public static void main(String[] args) throws Exception {
//        String fileName = "src/main/resources/all_FSMs/"+args[0]+".dot";
//        String fileName = "src/main/resources/test_fsms/"+args[0]+".dot";
//        String fileName = "src/main/resources/test2.dot";
//
//
//        CompactMealy<String, String> targetMealy = FileManager.loadMealyFromDotFile(new File(fileName));
//
//        SUL<String, String> sul = new MealySimulatorSUL<>(targetMealy);
//        OutputOracle<String, String> outputOracle = new OutputOracle<>(sul);
//        TestOracle<String, String> testOracle = new TestOracle<>(sul);
//        StatisticSUL<String, String> symbolCounterSUL = new SymbolCounterSUL<>("symbolCounter", sul);
//        StatisticSUL<String, String> resetCounterSUL = new ResetCounterSUL<>("resetCounter", symbolCounterSUL);
//
//
//        EquivalenceOracle<MealyMachine<?, String, ?, String>, String, Word<String>> equivalenceOracle = new RandomWordsEQOracle<>(
//                new SULOracle<>(resetCounterSUL),
//                2,
//                6,
//                3000,
//                new Random(System.currentTimeMillis())
//        );
//

//        HashSet<String> alphabet = new HashSet<>(targetMealy.getInputAlphabet());
//        testOracle.findCounterExample(targetMealy, alphabet);
////        CLSharp<String, String> clSharp = new CLSharp<>(outputOracle, testOracle, alphabet);
////        List<CompactMealy<String, String>> fsm = clSharp.learnComponents();
//
//        System.out.println("CLSharp:");
//        System.out.println(testOracle.getResetsCount());
//        System.out.println(testOracle.getSymbolsCount());
//        System.out.println(outputOracle.getResetsCount());
//        System.out.println(outputOracle.getSymbolsCount());
        runTests();
    }

    public static void runTests() throws Exception {
        File csvFile = new File("results.csv");
        FileWriter fileWriter = new FileWriter(csvFile);

        fileWriter.write(",TestResets, , , , TestSymbols, , , , LearnResets, , , , LearnSymbols, , , , minCEResets, , , , minCESymbols, , , , algResets, , , , algSymbols, , , \n");
        fileWriter.write("file,l-sharp,clsharp,nim-clsharp,cclsharp,l-sharp,clsharp,nim-clsharp,cclsharp,l-sharp,clsharp,nim-clsharp,cclsharp,l-sharp,clsharp,nim-clsharp,cclsharp,l-sharp,clsharp,nim-clsharp,cclsharp,l-sharp,clsharp,nim-clsharp,cclsharp,l-sharp,clsharp,nim-clsharp,cclsharp,l-sharp,clsharp,nim-clsharp,cclsharp\n");
        for (int j = 1; j < 197; j++) {
            if (j == 95) continue;
            String fileName = "src/main/resources/all_FSMs/"+j+".dot";
            for (int i = 0; i < 1; i++) {
                System.out.println("\n\n\nFile " + j);
                CompactMealy<String, String> targetMealy = FileManager.loadMealyFromDotFile(new File(fileName));
                OutputOracle<String, String> outputOracle2 = new OutputOracle<>(targetMealy);
                TestOracle<String, String> testOracle2 = new PerfectTestOracle<>(targetMealy);
                TestOracle<String, String> perfectOralce = new PerfectTestOracle<>(targetMealy);
                HashSet<String> alphabet2 = new HashSet<>(targetMealy.getInputAlphabet());
                
                LSharp<String, String> lSharp = new LSharp<>(outputOracle2, testOracle2, perfectOralce, alphabet2, Rule2.Ads, Rule3.Ads);
                LearnResult lResult = lSharp.learnMealy();
                System.out.println("\nLSharp");
                int lshTR = testOracle2.getResetsCount();
                int lshTS = testOracle2.getSymbolsCount();
                int lshLR = outputOracle2.getResetsCount();
                int lshLS = outputOracle2.getSymbolsCount();
//                System.out.println("Test " + testOracle2.getResetsCount());
//                System.out.println("Test " + testOracle2.getSymbolsCount());
//                System.out.println("Learn " + outputOracle2.getResetsCount());
//                System.out.println("Learn " + outputOracle2.getSymbolsCount());



                OutputOracle<String, String> outputOracle3 = new OutputOracle<>(targetMealy);
                TestOracle<String, String> testOracle3 = new PerfectTestOracle<>(targetMealy);

                HashSet<String> alphabet3 = new HashSet<>(targetMealy.getInputAlphabet());
                CCLSharp<String, String> cclSharp = new CCLSharp<>(outputOracle3, testOracle3, alphabet3);
                List<CompactMealy<String, String>> fsm3 = cclSharp.learnComponents();

                System.out.println("\nCLSharp");


                int clTR = testOracle3.getResetsCount();
                int clTS = testOracle3.getSymbolsCount();
                int clLR = outputOracle3.getResetsCount();
                int clLS = outputOracle3.getSymbolsCount();
                int clmR = cclSharp.getMinCEResets();
                int clmS = cclSharp.getMinCESymbols();
                int clalR = clLR - clmR;
                int clalS = clLS - clmS;

//                System.out.println("Test " + testOracle3.getResetsCount());
//                System.out.println("Test " + testOracle3.getSymbolsCount());
//                System.out.println("Learn " + outputOracle3.getResetsCount());
//                System.out.println("Learn " + outputOracle3.getSymbolsCount());
//                System.out.println("Learn min ce resets: " + cclSharp.getMinCEResets());
//                System.out.println("Learn min ce symbols: " + cclSharp.getMinCESymbols());



                OutputOracle<String, String> outputOracle4 = new OutputOracle<>(targetMealy);
                TestOracle<String, String> testOracle4 = new PerfectTestOracle<>(targetMealy);

                HashSet<String> alphabet4 = new HashSet<>(targetMealy.getInputAlphabet());
                NimCLSharp<String, String> nimCLSharp = new NimCLSharp<>(outputOracle4, testOracle4, alphabet4);
                List<CompactMealy<String, String>> fsm4 = nimCLSharp.learnComponents();

                System.out.println("\nNimCLSharp");


                int nimTR = testOracle4.getResetsCount();
                int nimLR = outputOracle4.getResetsCount();
                int nimTS = testOracle4.getSymbolsCount();
                int nimLS = outputOracle4.getSymbolsCount();
                int nimmR = nimCLSharp.getMinCEResets();
                int nimmS = nimCLSharp.getMinCESymbols();
                int nimalR = nimLR - nimmR;
                int nimalS = nimLS - nimmS;

//                System.out.println("Test " + testOracle4.getResetsCount());
//                System.out.println("Test " + testOracle4.getSymbolsCount());
//                System.out.println("Learn " + outputOracle4.getResetsCount());
//                System.out.println("Learn " + outputOracle4.getSymbolsCount());
//                System.out.println("Learn min ce resets: " + nimCLSharp.getMinCEResets());
//                System.out.println("Learn min ce symbols: " + nimCLSharp.getMinCESymbols());


                OutputOracle<String, String> outputOracle = new OutputOracle<>(targetMealy);
                TestOracle<String, String> testOracle = new PerfectTestOracle<>(targetMealy);

                HashSet<String> alphabet = new HashSet<>(targetMealy.getInputAlphabet());
                CLSharp<String, String> clSharp = new CLSharp<>(outputOracle, testOracle, alphabet);
                List<CompactMealy<String, String>> fsm = clSharp.learnComponents();
                System.out.println("\nCCLSharp:");


                int cclTR = testOracle.getResetsCount();
                int cclTS = testOracle.getSymbolsCount();
                int cclLR = outputOracle.getResetsCount();
                int cclLS = outputOracle.getSymbolsCount();
                int cclmR = clSharp.getMinCEResets();
                int cclmS = clSharp.getMinCESymbols();
                int cclalR = cclLR - cclmR;
                int cclalS = cclLS - cclmS;
//
//                System.out.println("Test " + testOracle.getResetsCount());
//                System.out.println("Test " + testOracle.getSymbolsCount());
//                System.out.println("Learn " + outputOracle.getResetsCount());
//                System.out.println("Learn " + outputOracle.getSymbolsCount());
//                System.out.println("Learn min ce resets: " + clSharp.getMinCEResets());
//                System.out.println("Learn min ce symbols: " + clSharp.getMinCESymbols());

                int[] intArr = {j, lshTR, clTR, nimTR, cclTR, lshTS, clTS, nimTS, cclTS, lshLR, clLR, nimLR, cclLR, lshLS, clLS, nimLS, cclLS, 0, clmR, nimmR, cclmR, 0, clmS, nimmS, cclmS, 0, clalR, nimalR, cclalR, 0, clalS, nimalS, cclalS};
                String[] stringArray = new String[intArr.length];
                for (int ii = 0; ii < intArr.length; ii++) stringArray[ii] = String.valueOf(intArr[ii]);

                fileWriter.write(String.join(",", stringArray) + "\n");
                fileWriter.flush();
//        for (CompactMealy<String, String> mealy : fsm) {
//            StringBuilder name = new StringBuilder();
//            for (String input : mealy.getInputAlphabet()) {
//                name.append(input).append("_");
//            }
//            FileManager.saveMealyToDotFile(mealy, new File("src/main/resources/test_results/result_"+args[0]+"_"+name+".dot"));
//        }
            }
        }
        fileWriter.close();
    }
}
