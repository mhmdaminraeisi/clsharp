package institute.teias.learner;

import institute.teias.oracles.OutputOracle;
import institute.teias.oracles.TestOracle;
import institute.teias.oracles.perfect.PerfectTestOracle;
import institute.teias.utils.Pair;
import lombok.Getter;
import net.automatalib.alphabet.Alphabet;
import net.automatalib.alphabet.Alphabets;
import net.automatalib.automaton.transducer.CompactMealy;

import java.util.*;

public class NCLSharp<I, O> {
    private final HashMap<HashSet<I>, CompactMealy<I, O>> hypos = new HashMap<>();
    private final TestOracle<I, O> testOracle;
    private final TestOracle<I ,O> partialTestOracle;
    private final OutputOracle<I, O> outputOracle;
    private final PerfectTestOracle<I, O> perfectTestOracle;
    private final HashSet<I> alphabet;
    private final Rule2 rule2;
    private final Rule3 rule3;
    @Getter private int minCEResets = 0;
    @Getter private int minCESymbols = 0;
    private final boolean cache;

    public NCLSharp(OutputOracle<I, O> outputOracle, TestOracle<I, O> testOracle, TestOracle<I, O> paratialTestOracle,
                   PerfectTestOracle<I, O> perfectTestOracle, HashSet<I> alphabet, Rule2 rule2, Rule3 rule3, boolean cache) {
        this.testOracle = testOracle;
        this.partialTestOracle = paratialTestOracle;
        this.outputOracle = outputOracle;
        this.perfectTestOracle = perfectTestOracle;
        this.alphabet = alphabet;
        this.rule2 = rule2;
        this.rule3 = rule3;
        this.cache = cache;

        for (I input : alphabet) {
            HashSet<I> alph = new HashSet<>(List.of(input));
            this.hypos.put(alph, null);
        }
    }

    private LearnResult<I, O> createResult(boolean isLearned, int rounds, int components) {
        int maxSize = 0;
        for (Map.Entry<HashSet<I>, CompactMealy<I, O>> entry : this.hypos.entrySet()) {
            if (maxSize < entry.getValue().size()) maxSize = entry.getValue().size();
        }
        return new LearnResult<>(
                outputOracle.getSymbolsCount(),
                outputOracle.getResetsCount(),
                testOracle.getSymbolsCount() + partialTestOracle.getSymbolsCount(),
                testOracle.getResetsCount() + partialTestOracle.getResetsCount(),
                rounds,
                components,
                minCESymbols,
                minCEResets,
                isLearned,
                null,
                maxSize
        );
    }


    public LearnResult<I, O> learnComponents() {
        int round = 1;
        while (true) {
            for (HashSet<I> alph : this.hypos.keySet()) {
                if (this.hypos.get(alph) == null) {
                    LSharp<I, O> lsharp = new LSharp<>(outputOracle, partialTestOracle, this.perfectTestOracle, alph, rule2, rule3, cache);
                    this.hypos.replace(alph, lsharp.learnMealy().hypothesis());
                }
            }
            CompactMealy<I, O> hypothesis = this.constructProductHypothesis(hypos.values().stream().toList());
            Pair<List<I>, List<O>> idealCe = this.perfectTestOracle.findCounterExample(hypothesis, this.alphabet);
            if (idealCe == null) {
                return createResult(true, round, this.hypos.size());
            }

            Pair<List<I>, List<O>> ce = this.testOracle.findCounterExample(hypothesis, this.alphabet);

            if (ce == null) {
                return createResult(false, round, this.hypos.size());
            }

            ce = this.cutCounterExample(ce, hypothesis);
            ce = this.minimiseCounterExample(ce, hypothesis);
            HashSet<I> newAlphabet = new HashSet<>();
            for (HashSet<I> alph : this.getInvolvedAlphabets(ce.first())) {
                this.hypos.remove(alph);
                newAlphabet.addAll(alph);
            }
            this.hypos.put(newAlphabet, null);
            round ++;
        }
    }

    private Pair<List<I>, List<O>> minimiseCounterExample(Pair<List<I>, List<O>> ce, CompactMealy<I, O> hypothesis) {
        List<HashSet<I>> involvedAlphabets = getInvolvedAlphabets(ce.first());

        for (int k = 2; k < involvedAlphabets.size(); k++) {
            for (List<HashSet<I>> subset : this.kCombinations(k, involvedAlphabets.size() - 1, involvedAlphabets)) {
                List<I> projectedCe = this.projectCounterExample(ce.first(), subset);
                minCESymbols += projectedCe.size();
                minCEResets += 1;
                List<O> outputs = this.outputOracle.answerQuery(projectedCe);
                List<O> hypothesisOutputs = hypothesis.computeStateOutput(hypothesis.getInitialState(), projectedCe).asList();
                if (!outputs.equals(hypothesisOutputs)) {
                    return new Pair<>(projectedCe, outputs);
                }
            }
        }
        return ce;
    }

    private List<List<HashSet<I>>> kCombinations(int k, int i, List<HashSet<I>> alphabets) {
        if (k == 0) return List.of(new ArrayList<>());
        if (i + 1 < k) return new ArrayList<>();

        List<List<HashSet<I>>> subList1 = this.kCombinations(k, i-1, alphabets);
        List<List<HashSet<I>>> subList2 = this.kCombinations(k-1, i-1, alphabets);
        subList2.forEach(l -> l.add(alphabets.get(i)));
        List<List<HashSet<I>>> result = new ArrayList<>();
        result.addAll(subList1);
        result.addAll(subList2);
        return result;
    }

    private List<I> projectCounterExample(List<I> ceInputs, List<HashSet<I>> pAlphabets) {
        List<I> projectedCeInputs = new ArrayList<>();
        for (I input : ceInputs) {
            for (HashSet<I> alph : pAlphabets) {
                if (alph.contains(input)) {
                    projectedCeInputs.add(input);
                    break;
                }
            }
        }
        return projectedCeInputs;
    }

    private Pair<List<I>, List<O>> cutCounterExample(Pair<List<I>, List<O>> ce, CompactMealy<I, O> hypothesis) {
        List<O> hypothesisOutputs = hypothesis.computeStateOutput(hypothesis.getInitialState(), ce.first()).asList();
        for (int i = 0; i < ce.second().size(); i++) {
            if (!hypothesisOutputs.get(i).equals(ce.second().get(i))) {
                return new Pair<>(ce.first().subList(0, i+1), ce.second().subList(0, i+1));
            }
        }
        throw new RuntimeException("Counter example outputs is equal to hypothesis outputs.");
    }

    private List<HashSet<I>> getInvolvedAlphabets(List<I> ceInputs) {
        HashSet<I> ceAlphabet = new HashSet<>(ceInputs);
        List<HashSet<I>> result = new ArrayList<>();
        int previousSize = ceAlphabet.size();
        for (HashSet<I> alph : this.hypos.keySet()) {
            ceAlphabet.removeAll(alph);
            if (ceAlphabet.size() < previousSize) {
                result.add(alph);
                previousSize = ceAlphabet.size();
            }
        }
        return result;
    }

    private CompactMealy<I, O> constructProductHypothesis(List<CompactMealy<I, O>> hypos) {
        Iterator<CompactMealy<I, O>> iterator = hypos.iterator();
        CompactMealy<I, O> productHypothesis = iterator.next();
        while (iterator.hasNext()) {
            productHypothesis = productTwoMealies(productHypothesis, iterator.next());
        }
        return productHypothesis;
    }

    private CompactMealy<I, O> productTwoMealies(CompactMealy<I, O> mealy1, CompactMealy<I, O> mealy2) {
        HashSet<I> mergedAlphabet = new HashSet<>();
        mergedAlphabet.addAll(mealy1.getInputAlphabet());
        mergedAlphabet.addAll(mealy2.getInputAlphabet());
        Alphabet<I> productAlphabet = Alphabets.fromCollection(mergedAlphabet);
        if (productAlphabet.size() != mealy1.getInputAlphabet().size() + mealy2.getInputAlphabet().size()) {
            throw new RuntimeException("The alphabets of two mealies must be separate.");
        }

        CompactMealy<I, O> result = new CompactMealy<>(productAlphabet);
        HashMap<Pair<Integer, Integer>, Integer> statesMap = new HashMap<>();
        Pair<Integer, Integer> initPair = new Pair<>(mealy1.getInitialState(), mealy2.getInitialState());
        statesMap.put(initPair, result.addState());
        result.setInitialState(statesMap.get(initPair));

        for (Integer state1 : mealy1.getStates()) {
            for (Integer state2 : mealy2.getStates()) {
                if (state1.equals(mealy1.getInitialState()) && state2.equals(mealy2.getInitialState())) continue;
                statesMap.put(new Pair<>(state1, state2), result.addState());
            }
        }

        for (Integer state1 : mealy1.getStates()) {
            for (Integer state2 : mealy2.getStates()) {
                for (I input : result.getInputAlphabet()) {
                    Integer dest1 = mealy1.getInputAlphabet().contains(input)
                            ? mealy1.getSuccessor(state1, input) : state1;
                    Integer dest2 = mealy2.getInputAlphabet().contains(input)
                            ? mealy2.getSuccessor(state2, input) : state2;
                    O output = mealy1.getInputAlphabet().contains(input)
                            ? mealy1.getOutput(state1, input) : mealy2.getOutput(state2, input);

                    Integer source = statesMap.get(new Pair<>(state1, state2));
                    Integer destination = statesMap.get(new Pair<>(dest1, dest2));
                    result.addTransition(source, input, destination, output);
                }
            }
        }
        return result;
    }

}
