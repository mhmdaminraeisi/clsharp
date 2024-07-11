package institute.teias.io;

import net.automatalib.alphabet.Alphabet;
import net.automatalib.alphabet.Alphabets;
import net.automatalib.automaton.transducer.CompactMealy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileManager {
    private static final Logger logger = LogManager.getLogger(FileManager.class);

    public static void deleteDirIfExistsAndCreateNew(String dirName) {
        File file = new File(dirName);
        if (file.exists()) {
            Arrays.stream(Objects.requireNonNull(file.list()))
                    .map(s -> new File(file.getPath(), s))
                    .forEach(File::deleteOnExit);
        } else file.mkdir();
    }

    public static void createDirIfNotExists(String dirName) {
        File file = new File(dirName);
        if (!file.exists()) file.mkdir();
    }

    public static void createFileIfNotExists(String dirName) throws IOException {
        File file = new File(dirName);
        if (!file.exists()) {
            file.createNewFile();
        }
    }

    public static CompactMealy<String, String> loadMealyFromDotFile(File dotFile) throws Exception {
        BufferedReader bufferedReader = new BufferedReader(new FileReader(dotFile));
        String regex = "^\\s*(\\w+)\\s*->\\s*(\\w+)\\s*\\[\\s*label\\s*=\\s*\"\\s*(.*?)\\s*/\\s*(.*?)\\s*\"\\s*\\](?:;)?";
        Pattern pattern = Pattern.compile(regex);

        List<String[]> groupsList = new ArrayList<>();

        while (bufferedReader.ready()) {
            String line = bufferedReader.readLine();
            Matcher matcher = pattern.matcher(line);

            if (matcher.matches()) {
                String[] groups = {matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4)};
                groupsList.add(groups);
            }
        }

        List<String> inputLabels = groupsList.stream().map(g -> g[2]).toList();
        Alphabet<String> alphabet = Alphabets.fromCollection(new TreeSet<>(inputLabels));

        CompactMealy<String, String> mealy = new CompactMealy<>(alphabet);
        HashMap<String, Integer> states = new HashMap<>();

        for (String[] group : groupsList) {
            if (!states.containsKey(group[0])) states.put(group[0], mealy.addState());
            if (!states.containsKey(group[1])) states.put(group[1], mealy.addState());
            Integer source = states.get(group[0]);
            Integer destination = states.get(group[1]);

            if (mealy.getInitialState() == null) {
                mealy.setInitialState(source);
                logger.info("Set {} as initial state!", group[0]);
            }

            String input = group[2];
            String output = group[3];

            if (mealy.getTransition(source, input) != null) {
                throw new Exception("Mealy machine is not deterministic.");
            }

            mealy.addTransition(source, input, destination, output);
        }

        for (Integer state : mealy.getStates()) {
            for (String input : mealy.getInputAlphabet()) {
                if (mealy.getTransition(state, input) == null) {
                    throw new Exception("Mealy machine is not complete.");
                }
            }
        }

        return mealy;
    }

    public static void saveMealyToDotFile(CompactMealy<String, String> mealy, File dotFile) throws IOException {
        BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(dotFile));

        bufferedWriter.append("digraph g {\n");
        ArrayList<Integer> states = new ArrayList<>(mealy.getStates());
        states.remove(mealy.getInitialState());
        states.addFirst(mealy.getInitialState());

        for (Integer state : states) {
            for (String input : mealy.getInputAlphabet()) {
                Integer nextState = mealy.getSuccessor(state, input);
                String output = mealy.getOutput(state, input);
                bufferedWriter.append(String.format("\ts%d -> s%d [label=\"%s/%s\"];\n",
                            state, nextState, input, output));
            }
        }

        bufferedWriter.append("}\n");
        bufferedWriter.close();
    }
}
