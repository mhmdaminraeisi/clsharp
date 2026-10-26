import java.io.*;
import java.util.*;
import java.util.regex.*;
import net.automatalib.automata.transducers.impl.compact.CompactMealy;
import net.automatalib.words.*;
import net.automatalib.words.impl.Alphabets;

/** DOT loading preserves the imported implementation's output representation. */
public final class Utils {
    public static final Word<String> OMEGA_SYMBOL = Word.fromLetter("Ω");
    private static final Utils INSTANCE = new Utils();
    public static Utils getInstance() { return INSTANCE; }
    public static String ExtractValue(String summary) { return summary.substring(summary.lastIndexOf(' ') + 1); }
    public CompactMealy<String, Word<String>> loadMealyMachineFromDot(File f) throws Exception {

        Pattern kissLine = Pattern.compile("\\s*([a-zA-Z0-9]+)\\s+->\\s+([a-zA-Z0-9]+)\\s*\\[label=[\"<](.+)[\">]\\];?");

        BufferedReader br = new BufferedReader(new FileReader(f));

        List<String[]> trs = new ArrayList<String[]>();

        HashSet<String> abcSet = new HashSet<>();


        String line;
        while ((line = br.readLine()) != null) {
            Matcher m = kissLine.matcher(line);
            if(m.matches()){

                String[] tr = new String[4];
                tr[0] = m.group(1);
                tr[1] = m.group(3);
                tr[3] = m.group(2);
                if(tr[1].contains("<br />")){
                    String trr[] = tr[1].split("<br />");
                    tr[1]=trr[0];
                    tr[2]=trr[1];
                    trr = tr[1].split(" \\| ");
                    for (String string : trr) {
                        String trrr[] = new String[4];
                        trrr[0]= tr[0];
                        trrr[1]= string;
                        trrr[2]= tr[2];
                        trrr[3]= tr[3];
                        trs.add(trrr);
                        abcSet.add(trrr[1]);
                    }
                }else{
                    String trr[] = tr[1].split("\\s*/\\s*");
                    tr[1]=trr[0];
                    try {
                        tr[2] = trr[1];
                    }catch (Exception e){
                        tr[2] = " ";
                    }
                    trs.add(tr);
                    abcSet.add(tr[1]);
                }
            }
        }

        br.close();

        List abc = new ArrayList<>(abcSet);
        Collections.sort(abc);
        Alphabet<String> alphabet = Alphabets.fromCollection(abc);
        CompactMealy<String, Word<String>> mealym = new CompactMealy<String, Word<String>>(alphabet);

        Map<String,Integer> states = new HashMap<String,Integer>();
        Integer si=null,sf=null;

        Map<String,Word<String>> words = new HashMap<String,Word<String>>();


        WordBuilder<String> aux = new WordBuilder<>();

        aux.clear();
        aux.append(OMEGA_SYMBOL);
        words.put(OMEGA_SYMBOL.toString(), aux.toWord());


        for (String[] tr : trs) {
            if(!states.containsKey(tr[0])) states.put(tr[0], mealym.addState());
            if(!states.containsKey(tr[3])) states.put(tr[3], mealym.addState());

            si = states.get(tr[0]);
            sf = states.get(tr[3]);

            if(!words.containsKey(tr[1])){
                aux.clear();
                aux.add(tr[1]);
                words.put(tr[1], aux.toWord());
            }
            if(!words.containsKey(tr[2])){
                aux.clear();
                aux.add(tr[2]);
                words.put(tr[2], aux.toWord());
            }
            mealym.addTransition(si, words.get(tr[1]).toString(), sf, words.get(tr[2]));
        }

        for (Integer st : mealym.getStates()) {
            for (String in : alphabet) {
                if(mealym.getTransition(st, in)==null){
                    mealym.addTransition(st, in, st, OMEGA_SYMBOL);
                }
            }
        }


        if (!states.containsKey("s0")) throw new IOException("Expected initial state s0 in " + f);
        mealym.setInitialState(states.get("s0"));

        return mealym;
    }

}
