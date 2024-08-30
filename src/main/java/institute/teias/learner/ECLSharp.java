package institute.teias.learner;

import institute.teias.oracles.OutputOracle;
import institute.teias.oracles.TestOracle;
import institute.teias.utils.Pair;
import lombok.Getter;
import net.automatalib.alphabet.Alphabet;
import net.automatalib.alphabet.Alphabets;
import net.automatalib.automaton.transducer.CompactMealy;

import java.util.*;

public class ECLSharp<I, O> {
    private final List<Component<I, O>> components = new ArrayList<>();
    private final TestOracle<I, O> testOracle;
    private final OutputOracle<I, O> outputOracle;
    private final HashSet<I> alphabet;
    @Getter private int minCEResets = 0;
    @Getter private int minCESymbols = 0;

    public ECLSharp(OutputOracle<I, O> outputOracle, TestOracle<I, O> testOracle, HashSet<I> alphabet) {
        this.testOracle = testOracle;
        this.outputOracle = outputOracle;
        this.alphabet = alphabet;
        for (I input : alphabet) {
            this.components.add(new Component<>(outputOracle, new HashSet<>(List.of(input))));
        }
    }

    public List<CompactMealy<I, O>> learnComponents() {
        int round = 1;
        while (true) {
            List<CompactMealy<I, O>> hypos = this.components.stream().map(this::learnComponent).toList();
            CompactMealy<I, O> hypothesis = this.constructProductHypothesis(hypos);
            Pair<List<I>, List<O>> ce = this.testOracle.findCounterExample(hypothesis);
            if (ce == null) return hypos;

            ce = this.cutCounterExample(ce, hypothesis);
            ce = this.minimiseCounterExample(ce, hypothesis);
            List<Component<I, O>> involvedComponents = this.getInvolvedComponents(ce.first());
            final I firstInput = ce.first().getFirst();
            Optional<Component<I, O>> o = involvedComponents.stream().filter(c -> c.getAlphabet().contains(firstInput)).findFirst();
            if (o.isEmpty()) {
                throw new RuntimeException("Something is wrong.");
            }

            Component<I, O> baseComponent = o.get();
            baseComponent.getObservationForest().insertObservation(ce.first(), ce.second());
            involvedComponents.remove(baseComponent);
            baseComponent.mergeWithComponents(involvedComponents, ce.first(), ce.second());
            this.components.removeAll(involvedComponents);
        }
    }

    private CompactMealy<I, O> learnComponent(Component<I, O> component) {
        while (true) {
            CompactMealy<I, O> hypothesis = component.buildHypothesis();
            Pair<List<I>, List<O>> ce = this.testOracle.findCounterExample(hypothesis);
            if (ce == null) return hypothesis;
            component.processCounterExample(hypothesis, ce.first(), ce.second());
        }
    }

    private Pair<List<I>, List<O>> minimiseCounterExample(Pair<List<I>, List<O>> ce, CompactMealy<I, O> hypothesis) {
        for (int k = 2; k <= this.components.size(); k++) {
            for (List<Component<I, O>> subset : this.kCombinations(k, 0)) {
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


    private List<List<Component<I, O>>> kCombinations(int k, int i) {
        if (k == 0) return List.of(new ArrayList<>());
        if (this.components.size() - i < k) return new ArrayList<>();

        List<List<Component<I, O>>> subList1 = this.kCombinations(k, i+1);
        List<List<Component<I, O>>> subList2 = this.kCombinations(k-1, i+1);
        subList2.forEach(l -> l.add(this.components.get(i)));
        List<List<Component<I, O>>> result = new ArrayList<>();
        result.addAll(subList1);
        result.addAll(subList2);
        return result;
    }

    private List<I> projectCounterExample(List<I> ceInputs, List<Component<I, O>> pComponents) {
        List<I> projectedCeInputs = new ArrayList<>();
        for (I input : ceInputs) {
            for (Component<I, O> c : pComponents) {
                if (c.getAlphabet().contains(input)) {
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

    private List<Component<I, O>> getInvolvedComponents(List<I> ceInputs) {
        HashSet<I> ceAlphabet = new HashSet<>(ceInputs);
        List<Component<I, O>> involvedComponents = new ArrayList<>();
        int previousSize = ceAlphabet.size();
        for (Component<I, O> component : this.components) {
            ceAlphabet.removeAll(component.getAlphabet());
            if (ceAlphabet.size() < previousSize) {
                involvedComponents.add(component);
                previousSize = ceAlphabet.size();
            }
        }
        return involvedComponents;
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
