package institute.teias.learner;

import com.google.common.collect.HashBiMap;
import institute.teias.ads.Ads;
import institute.teias.obsTree.Node;
import institute.teias.obsTree.NormalObservationTree;
import institute.teias.obsTree.ObservationTree;
import institute.teias.oracles.OutputOracle;
import institute.teias.oracles.TestOracle;
import institute.teias.utils.Pair;
import net.automatalib.alphabet.Alphabet;
import net.automatalib.alphabet.Alphabets;
import net.automatalib.automaton.transducer.CompactMealy;

import java.util.*;
import java.util.stream.Collectors;

public class LSharp<I, O> {
    private final OutputOracle<I, O> outputOracle;
    private final TestOracle<I, O> testOracle;
    private final TestOracle<I, O> perfectOracle;
    private final HashSet<I> alphabet;
    private final ObservationTree<I, O> observationTree;
    private final HashSet<Node<I, O>> basis = new HashSet<>();
    private final HashMap<Node<I, O>, HashSet<Node<I, O>>> frontierToBasisMap = new HashMap<>();
    private final HashMap<Pair<Node<I, O>, Node<I, O>>, List<I>> witnessCache = new HashMap<>();
    private final HashBiMap<Node<I, O>, Integer> basisToStateMap = HashBiMap.create();
    private final Rule2 rule2;
    private final Rule3 rule3;
    private int previousBasisSize = 0;

    public LSharp(
            OutputOracle<I, O> outputOracle,
            TestOracle<I, O> testOracle,
            TestOracle<I, O> perfectOracle,
            HashSet<I> alphabet,
            Rule2 rule2,
            Rule3 rule3
    ) {
        this.rule2 = rule2;
        this.rule3 = rule3;
        this.outputOracle = outputOracle;
        this.testOracle = testOracle;
        this.perfectOracle = perfectOracle;
        this.alphabet = alphabet;
        this.observationTree = new NormalObservationTree<>(this.alphabet);
        this.basis.add(this.observationTree.getRoot());
    }

    public LearnResult learnMealy() {
        int round = 0;
        while (true) {
            round += 1;
            CompactMealy<I, O> hypothesis = this.buildHypothesis();
            Pair<List<I>, List<O>> idealCe = this.perfectOracle.findCounterExample(hypothesis);
            if (idealCe == null) return this.createResult(true, round);
            Pair<List<I>, List<O>> ce = this.testOracle.findCounterExample(hypothesis);
            if (ce == null) {
                System.out.println("No CE found!");
                return this.createResult(false, round);
            };
            this.processCounterExample(hypothesis, ce.first(), ce.second());
        }
    }

    private LearnResult createResult(boolean isLearned, int rounds) {
        return new LearnResult(
                outputOracle.getSymbolsCount(),
                outputOracle.getResetsCount(),
                testOracle.getSymbolsCount(),
                testOracle.getResetsCount(),
                rounds,
                isLearned

        );
    }

    private CompactMealy<I, O> buildHypothesis() {
        while (true) {
            this.makeObservationTreeAdequate();
            CompactMealy<I, O> hypothesis = this.constructHypothesis();
            // check consistency
            List<I> counterExample = Apartness.computeWitnessInTreeAndHypothesis(this.observationTree, hypothesis);
            // ToDo last ce consistency!
            if (counterExample == null) {
                return hypothesis;
            }
            List<O> ceOutputs = this.observationTree.getObservation(this.observationTree.getRoot(), counterExample);
            this.processCounterExample(hypothesis, counterExample, ceOutputs);
        }
    }

    private void processCounterExample(CompactMealy<I, O> hypothesis, List<I> ceInputs, List<O> ceOutputs) {
        // ToDo clear rule caches.
        this.observationTree.insertObservation(this.observationTree.getRoot(), ceInputs, ceOutputs);
        List<O> hypothesisOutputs = hypothesis.computeOutput(ceInputs).asList();
        int prefixIndex = this.getCounterExamplePrefixIndex(ceOutputs, hypothesisOutputs);
        this.processBinarySearch(hypothesis, ceInputs.subList(0, prefixIndex), ceOutputs.subList(0, prefixIndex));
        // ToDo cache rules.
    }

    private int getCounterExamplePrefixIndex(List<O> ceOutputs, List<O> hypothesisOutputs) {
        // Todo Fix this?
        for (int i = 0; i < ceOutputs.size(); i++) {
            if (!ceOutputs.get(i).equals(hypothesisOutputs.get(i))) return i;
        }
        throw new RuntimeException("Sul and Hypothesis do not disagree on CE inputs.");
    }

    private void processBinarySearch(CompactMealy<I, O> hypothesis, List<I> ceInputs, List<O> ceOutputs) {
        Node<I, O> r = this.observationTree.getSuccessor(this.observationTree.getRoot(), ceInputs);
        this.updateFrontierAndBasis();
        if (this.frontierToBasisMap.containsKey(r) || this.basis.contains(r)) {
            return;
        }
        Integer q_state = hypothesis.getSuccessor(hypothesis.getInitialState(), ceInputs);
        Node<I, O> q = this.basisToStateMap.inverse().get(q_state);

        List<I> prefix = new ArrayList<>();
        Node<I, O> currentNode = this.observationTree.getRoot();
        for (I input : ceInputs) {
            if (this.frontierToBasisMap.containsKey(currentNode)) break;
            currentNode = currentNode.getSuccessor(input);
            prefix.add(input);
        }

        int h = (prefix.size() + ceInputs.size()) / 2;
        List<I> sigma1 = ceInputs.subList(0, h);
        List<I> sigma2 = ceInputs.subList(h, ceOutputs.size());

        Integer q_p_state = hypothesis.getSuccessor(hypothesis.getInitialState(), sigma1);
        Node<I, O> q_p = this.basisToStateMap.inverse().get(q_p_state);
        List<I> q_p_access = this.observationTree.getAccessSequence(q_p);

        List<I> witness = Apartness.computeWitness(r, q, this.observationTree);
        if (witness == null) {
            throw new RuntimeException("Something is wrong.");
        }

        List<I> queryInputs = new ArrayList<>(q_p_access);
        queryInputs.addAll(sigma2);
        queryInputs.addAll(witness);
        List<O> queryOutputs = this.outputOracle.answerQuery(queryInputs);
        this.observationTree.insertObservation(this.observationTree.getRoot(), queryInputs, queryOutputs);

        Node<I, O> r_p = this.observationTree.getSuccessor(this.observationTree.getRoot(), sigma1);
        List<I> witness_p = Apartness.computeWitness(r_p, q_p, this.observationTree);
        if (witness_p != null) {
            this.processBinarySearch(hypothesis, sigma1, ceOutputs.subList(0, h));
        } else {
            List<I> newInputs = new ArrayList<>(q_p_access);
            newInputs.addAll(sigma2);
            this.processBinarySearch(hypothesis, newInputs, queryOutputs.subList(0, newInputs.size()));
        }
    }

    private CompactMealy<I, O> constructHypothesis() {
        Alphabet<I> hypothesisAlphabet = Alphabets.fromCollection(new TreeSet<>(this.alphabet));
        CompactMealy<I, O> hypothesis = new CompactMealy<>(hypothesisAlphabet);

        Node<I, O> root = this.observationTree.getRoot();
        this.basisToStateMap.clear();
        this.basisToStateMap.put(root, hypothesis.addState());
        hypothesis.setInitialState(this.basisToStateMap.get(root));

        for (Node<I, O> basisState : this.basis) {
            if (root == basisState) continue;
            this.basisToStateMap.put(basisState, hypothesis.addState());
        }

        for (Node<I, O> basisState : this.basis) {
            for (I input : this.alphabet) {
                Integer source = this.basisToStateMap.get(basisState);
                O output = basisState.getOutput(input);
                Node<I, O> successor = basisState.getSuccessor(input);
                if (this.frontierToBasisMap.containsKey(successor)) {
                    HashSet<Node<I, O>> candidates = this.frontierToBasisMap.get(successor);
                    if (candidates.size() > 1) {
                        throw new RuntimeException("Multiple basis candidates for a single frontier state.");
                    }
                    successor = candidates.iterator().next();
                }
                if (!this.basisToStateMap.containsKey(successor)) {
                    throw new RuntimeException("Something is wrong.");
                }
                Integer destination = this.basisToStateMap.get(successor);
                hypothesis.addTransition(source, input, destination, output);
            }
        }
        return hypothesis;
    }

    private void makeObservationTreeAdequate() {
        this.updateFrontierAndBasis();
        while (!this.isObservationTreeAdequate()) {
            this.makeBasisComplete();
            this.makeFrontiersIdentified();
            this.promoteFrontierState();
        }
    }

    private boolean isObservationTreeAdequate() {
        this.checkFrontierConsistency();
        if (this.frontierToBasisMap.entrySet().stream().anyMatch(entry -> entry.getValue().size() != 1)) {
            return false;
        }
        for (Node<I, O> basisState : this.basis) {
            for (I input : this.alphabet) {
                if (basisState.getOutput(input) == null) return false;
            }
        }
        return true;
    }

    /** Applying Rule 3: Identify all frontier states. */
    private void makeFrontiersIdentified() {
        for (Node<I, O> frontierState : this.frontierToBasisMap.keySet()) {
            this.identifyFrontier(frontierState);
        }
    }

    private void identifyFrontier(Node<I, O> frontierState) {
        if (!this.frontierToBasisMap.containsKey(frontierState)) {
            throw new RuntimeException("Frontier state does not exists.");
        }
        this.updateBasisCandidates(frontierState);
        int previousCandidatesSize = this.frontierToBasisMap.get(frontierState).size();
        if (previousCandidatesSize < 2) return;

        Pair<List<I>, List<O>> inputsOutputs = rule3.equals(Rule3.SepSeq) || previousCandidatesSize == 2
                ? this.identifyFrontierSepSeq(frontierState)
                : this.identifyFrontierAds(frontierState);


        this.observationTree.insertObservation(inputsOutputs.first(), inputsOutputs.second());

        this.updateBasisCandidates(frontierState);
        if (this.frontierToBasisMap.get(frontierState).size() == previousCandidatesSize) {
            throw new RuntimeException("Did not increase norm.");
        }
    }

    private Pair<List<I>, List<O>> identifyFrontierSepSeq(Node<I, O> frontierNode) {
        HashSet<Node<I, O>> basisCandidates = this.frontierToBasisMap.get(frontierNode);
        Iterator<Node<I, O>> iterator = basisCandidates.iterator();
        Node<I, O> basisOne = iterator.next();
        Node<I, O> basisTwo = iterator.next();

        List<I> witness = getOrComputeWitness(basisOne, basisTwo);
        List<I> inputs = this.observationTree.getAccessSequence(frontierNode);
        inputs.addAll(witness);
        List<O> outputs = this.outputOracle.answerQuery(inputs);
        return new Pair<>(inputs, outputs);
    }

    private Pair<List<I>, List<O>> identifyFrontierAds(Node<I, O> frontierNode) {
        // Todo oldBasisCandidates
        return this.identifyFrontierAdsNoCache(frontierNode);
    }

    private Pair<List<I>, List<O>> identifyFrontierAdsNoCache(Node<I, O> prefix) {
        HashSet<Node<I, O>> basisCandidates = this.frontierToBasisMap.get(prefix);

        Ads<I, O> suffix = new Ads<>(this.observationTree, basisCandidates);
        return this.adaptiveOutputQuery(this.observationTree.getAccessSequence(prefix), suffix);
    }

    private Pair<List<I>, List<O>> adaptiveOutputQuery(List<I> prefix, I infix, Ads<I, O> suffix) {
        List<I> newPrefix = new ArrayList<>(prefix);
        newPrefix.add(infix);
        return this.adaptiveOutputQuery(newPrefix, suffix);
    }

    private Pair<List<I>, List<O>> adaptiveOutputQuery(List<I> prefix, Ads<I, O> suffix) {
        Node<I, O> fromNode = this.observationTree.getSuccessor(prefix);

        if (fromNode != null) {
            Pair<List<I>, List<O>> treeReply = this.answerAdsFromTree(suffix, fromNode);
            suffix.resetToRoot();
            if (treeReply != null) {
                List<I> inputs = new ArrayList<>(prefix);
                List<O> outputs = this.observationTree.getObservation(prefix);
                inputs.addAll(treeReply.first());
                outputs.addAll(treeReply.second());
                return new Pair<>(inputs, outputs);
            }
        }

        List<O> outputs = new ArrayList<>(this.outputOracle.answerQuery(prefix));
        Pair<List<I>, List<O>> sulReply = this.sulAdaptiveQuery(suffix);
        List<I> inputs = new ArrayList<>(prefix);
        inputs.addAll(sulReply.first());
        outputs.addAll(sulReply.second());
        this.observationTree.insertObservation(inputs, outputs);

        return new Pair<>(inputs, outputs);
    }

    // Assuming the prefix has been sent to the output oracle, perform the adaptive query.
    private Pair<List<I>, List<O>> sulAdaptiveQuery(Ads<I, O> ads) {
        List<I> inputsSent = new ArrayList<>();
        List<O> outputsReceived = new ArrayList<>();
        O lastOutput = null;

        while (true) {
            I nextInput = ads.nextInput(lastOutput);
            if (nextInput == null) break;
            inputsSent.add(nextInput);
            O output = this.outputOracle.answerQueryWithoutReset(nextInput);
            outputsReceived.add(output);
            lastOutput = output;
        }
        return new Pair<>(inputsSent, outputsReceived);
    }

    private Pair<List<I>, List<O>> answerAdsFromTree(Ads<I, O> ads, Node<I, O> fromNode) {
        O prevOutput = null;
        List<I> inputsSent = new ArrayList<>();
        List<O> outputsReceived = new ArrayList<>();
        Node<I, O> currentNode = fromNode;

        while (true) {
            I nextInput = ads.nextInput(prevOutput);
            if (nextInput == null) break;
            inputsSent.add(nextInput);
            Pair<O, Node<I, O>> outputSuccessor = currentNode.getOutputSuccessor(nextInput);
            if (outputSuccessor == null) return null;
            prevOutput = outputSuccessor.first();
            outputsReceived.add(outputSuccessor.first());
            currentNode = outputSuccessor.second();
        }
        ads.resetToRoot();
        return new Pair<>(inputsSent, outputsReceived);
    }

    private List<I> getOrComputeWitness(Node<I, O> nodeOne, Node<I, O> nodeTwo) {
        Pair<Node<I, O>, Node<I, O>> pairOne = new Pair<>(nodeOne, nodeTwo);
        if (this.witnessCache.containsKey(pairOne)) {
            return this.witnessCache.get(pairOne);
        }
        Pair<Node<I, O>, Node<I, O>> pairTwo = new Pair<>(nodeTwo, nodeOne);
        if (this.witnessCache.containsKey(pairTwo)) {
            return this.witnessCache.get(pairTwo);
        }
        List<I> witness = Apartness.computeWitness(nodeOne, nodeTwo, this.observationTree);
        this.witnessCache.put(pairOne, witness);
        this.witnessCache.put(pairTwo, witness);
        return witness;
    }

    /** Applying Rule 2: Explores the frontier for all basis states. */
    private void makeBasisComplete() {
        for (Node<I, O> basisState : this.basis) {
            for (I input : this.alphabet) {
                if (basisState.getSuccessor(input) == null) {
                    this.exploreFrontier(basisState, input);
                    Node<I, O> newFrontier = basisState.getSuccessor(input);
                    HashSet<Node<I, O>> basisCandidates = this.basis.stream()
                            .filter(b -> !Apartness.statesAreApart(newFrontier, b, this.observationTree))
                            .collect(Collectors.toCollection(HashSet::new));
                    this.frontierToBasisMap.put(newFrontier, basisCandidates);
                }
            }
        }
    }

    private void exploreFrontier(Node<I, O> basisState, I input) {
        Pair<List<I>, List<O>> inputsOutputs = this.getInputsOutputsRule2(basisState, input);
        this.observationTree.insertObservation(inputsOutputs.first(), inputsOutputs.second());
    }

    private Pair<List<I>, List<O>> getInputsOutputsRule2(Node<I, O> basisState, I input) {
        boolean updateR2Ads = (double) this.basis.size() >= 1.1 * (double) previousBasisSize;
        if (updateR2Ads) {
            this.previousBasisSize = this.basis.size();
        }
        if (rule2.equals(Rule2.Ads)) {
            Ads<I, O> suffix = new Ads<>(this.observationTree, new HashSet<>(this.basis));
            return this.adaptiveOutputQuery(this.observationTree.getAccessSequence(basisState), input, suffix);
        }
        if (rule2.equals(Rule2.Nothing) || rule2.equals(Rule2.SepSeq) && this.basis.size() == 1) {
            List<I> inputs = this.observationTree.getAccessSequence(basisState);
            inputs.add(input);
            List<O> outputs = this.outputOracle.answerQuery(inputs);
            return new Pair<>(inputs, outputs);
        }
        if (rule2.equals(Rule2.SepSeq)) {
            Iterator<Node<I, O>> iterator = basis.iterator();
            Node<I, O> basisOne = iterator.next();
            Node<I, O> basisTwo = iterator.next();

            List<I> witness = getOrComputeWitness(basisOne, basisTwo);
            List<I> inputs = this.observationTree.getAccessSequence(basisState);
            inputs.add(input);
            inputs.addAll(witness);
            List<O> outputs = this.outputOracle.answerQuery(inputs);
            return new Pair<>(inputs, outputs);
        }
        return null;
    }

    private void updateFrontierAndBasis() {
        this.updateFrontierToBasisMap();
        this.promoteFrontierState();
        this.checkFrontierConsistency();
        this.updateFrontierToBasisMap();
    }

    private void updateBasisCandidates(Node<I, O> frontierState) {
        this.frontierToBasisMap.replace(frontierState, this.frontierToBasisMap.get(frontierState)
                .stream().filter(b -> !Apartness.statesAreApart(frontierState, b, this.observationTree))
                .collect(Collectors.toCollection(HashSet::new)));
    }

    private void updateFrontierToBasisMap() {
        this.frontierToBasisMap.replaceAll((f, basisCandidates) -> basisCandidates.stream()
                .filter(b -> !Apartness.statesAreApart(f, b, this.observationTree))
                .collect(Collectors.toCollection(HashSet::new)));
    }

    private void promoteFrontierState() {
        Optional<Node<I, O>> isolated = this.frontierToBasisMap.entrySet().stream()
                .filter(entry -> entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .findFirst();

        if (isolated.isEmpty()) return;

        Node<I, O> newBasis = isolated.get();
        this.basis.add(newBasis);
        this.frontierToBasisMap.keySet().remove(newBasis);

        this.frontierToBasisMap.replaceAll((f, basisCandidates) -> {
            if (!Apartness.statesAreApart(f, newBasis, this.observationTree)) {
                basisCandidates.add(newBasis);
            }
            return basisCandidates;
        });
    }

    private void checkFrontierConsistency() {
        for (Node<I, O> basisNode : this.basis) {
            for (I input : this.observationTree.getAlphabet()) {
                Node<I, O> frontierMaybe = basisNode.getSuccessor(input);
                if (frontierMaybe == null || this.basis.contains(frontierMaybe) || this.frontierToBasisMap.containsKey(frontierMaybe)) {
                    continue;
                }

                HashSet<Node<I, O>> basisCandidates = this.basis.stream()
                        .filter(b -> !Apartness.statesAreApart(frontierMaybe, b, this.observationTree))
                        .collect(Collectors.toCollection(HashSet::new));

                frontierToBasisMap.put(frontierMaybe, basisCandidates);
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder();
        buffer.append("##################################################\n");
        buffer.append("observation tree: \n");
        buffer.append(observationTree.toString());
        buffer.append("\n");
        buffer.append("basis: \n");
        for (Node<I, O> b : this.basis) {
            if (b.getInputOutput() != null) {
                buffer.append(b.getInputOutput().first()).append("|").append(b.getInputOutput().second());
            }
            buffer.append(this.observationTree.getAccessSequence(b)).append("\n");
        }
        buffer.append("\n").append("frontier: \n");
        for (Map.Entry<Node<I, O>, HashSet<Node<I, O>>> f : this.frontierToBasisMap.entrySet()) {
            buffer.append(this.observationTree.getAccessSequence(f.getKey())).append("  :  ");
            for (Node<I, O> b : f.getValue()) {
                buffer.append(this.observationTree.getAccessSequence(b)).append(" - ");
            }
            buffer.append("\n");
        }
        buffer.append("____________________________________________________\n");
        return buffer.toString();
    }
}
