package institute.teias.learner;

import institute.teias.ads.Ads;
import institute.teias.obsTree.BaseObservation;
import institute.teias.obsTree.Node;
import institute.teias.oracles.OutputOracle;
import institute.teias.utils.Pair;
import lombok.Getter;
import lombok.Setter;
import net.automatalib.automaton.transducer.CompactMealy;

import java.util.*;

public abstract class BaseLSharp<I, O> {
    protected final OutputOracle<I, O> outputOracle;
    @Getter protected final HashSet<I> alphabet;
    @Setter private BaseObservation<I, O> obsTool;
    protected final Rule2 rule2;
    protected final Rule3 rule3;
    @Getter protected final HashSet<Node<I, O>> basis = new HashSet<>();
    @Getter protected final HashSet<Node<I, O>> frontiers = new HashSet<>();
    @Getter protected final HashMap<Node<I, O>, HashSet<Node<I, O>>> apartnessGraph = new HashMap<>();
    private final HashMap<Pair<Node<I, O>, Node<I, O>>, List<I>> witnessCache = new HashMap<>();
    private int previousBasisSize = 0;
    private final boolean cache;
    private final HashMap<HashSet<Node<I, O>>, Ads<I, O>> adsCache = new HashMap<>();

    public BaseLSharp(
            OutputOracle<I, O> outputOracle,
            HashSet<I> alphabet,
            Rule2 rule2,
            Rule3 rule3,
            boolean cache
    ) {
        this.outputOracle = outputOracle;
        this.alphabet = alphabet;
        this.rule2 = rule2;
        this.rule3 = rule3;
        this.cache = cache;
    }

    public CompactMealy<I, O> buildHypothesis() {
        while (true) {
            this.makeObservationsAdequate();
            CompactMealy<I, O> hypothesis = this.constructHypothesis();
            // check consistency
            List<I> counterExample = this.findCounterExample(hypothesis);
            // ToDo last ce consistency!
            if (counterExample == null) {
                return hypothesis;
            }
            List<O> ceOutputs = this.obsTool.getObservation(counterExample);
            this.processCounterExample(hypothesis, counterExample, ceOutputs);
        }
    }

    public void processCounterExample(CompactMealy<I, O> hypothesis, List<I> ceInputs, List<O> ceOutputs) {
        // ToDo clear rule caches.
        this.adsCache.clear();
        this.obsTool.insertObservation(ceInputs, ceOutputs);
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

    protected void makeObservationsAdequate() {
        this.updateFrontierAndBasis();
        while (!this.isObservationsAdequate()) {
            this.applyRuleThree();
            this.applyRuleTwo();
            this.applyRuleOne();
        }
    }

    private boolean isObservationsAdequate() {
        this.makeFrontierConsistentWithBasis();
        if (this.findIsolatedFrontier() != null) return false;
        for (Node<I, O> node : this.apartnessGraph.keySet()) {
            if (this.getTwoApartInGroup(this.apartnessGraph.get(node)) != null) {
                return false;
            }
        }
        for (Node<I, O> basisNode : this.basis) {
            if (!isBasisNodeComplete(basisNode)) return false;
        }
        return true;
    }

    /** Applying Rule 3: Identify all apartness graph states. */
    protected void applyRuleThree() {
        this.updateGraphEdges();
        for (Node<I, O> state : this.apartnessGraph.keySet()) {
            this.identifyState(state);
        }
    }

    private void identifyState(Node<I, O> state) {
        if (!this.apartnessGraph.containsKey(state)) {
            throw new RuntimeException("The state does not exists in apartness graph.");
        }
        this.updateNodeEdges(state);
        int previousEdgesCount = this.apartnessGraph.get(state).size();

        if (previousEdgesCount == 0 || this.isStateIdentified(state)) return;

        Pair<List<I>, List<O>> inputsOutputs = rule3.equals(Rule3.SepSeq) || hasOnlyOneApartPairNeighbours(state)
                ? this.identifyStateSepSeq(state)
                : this.identifyStateAds(state);

        this.obsTool.insertObservation(inputsOutputs.first(), inputsOutputs.second());
        this.updateEdgesAfterIdentifyState(state);

        if (this.apartnessGraph.get(state).size() == previousEdgesCount) {
            throw new RuntimeException("Did not increase norm.");
        }
    }

    private void updateEdgesAfterIdentifyState(Node<I, O> state) {
        this.updateNodeEdges(state);
        HashSet<Node<I, O>> neighbours = new HashSet<>(this.apartnessGraph.get(state));
        for (Node<I, O> node : neighbours) {
            if (this.apartnessGraph.containsKey(node)) {
                this.updateNodeEdges(node);
            }
        }
    }

    private Pair<List<I>, List<O>> identifyStateSepSeq(Node<I, O> state) {
        Pair<Node<I, O>, Node<I, O>> apartPair = this.getTwoApartInGroup(this.apartnessGraph.get(state));
        if (apartPair == null) {
            throw new RuntimeException("State has no two apart neighbours!");
        }
        List<I> witness = getOrComputeWitness(apartPair.first(), apartPair.second());
        if (witness == null) {
            throw new RuntimeException("Witness must not be null.");
        }
        List<I> inputs = this.obsTool.getAccessSequence(state);
        inputs.addAll(witness);
        List<O> outputs = this.outputOracle.answerQuery(inputs);
        return new Pair<>(inputs, outputs);
    }

    private Pair<List<I>, List<O>> identifyStateAds(Node<I, O> prefix) {
        // Todo oldBasisCandidates
        HashSet<Node<I, O>> stateNeighbours = this.apartnessGraph.get(prefix);
        Ads<I, O> suffix = this.getAds(stateNeighbours);
        return this.adaptiveOutputQuery(this.obsTool.getAccessSequence(prefix), suffix);
    }

    /** Applying Rule 2: Explores the frontier for all basis states. */
    protected void applyRuleTwo() {
        for (Node<I, O> basisNode : this.basis) {
            for (I input : this.alphabet) {
                if (this.isBasisMustExploreWithInput(basisNode, input)) {
                    this.exploreFrontier(basisNode, input);
                }
            }
        }
    }

    private void exploreFrontier(Node<I, O> basisState, I input) {
        Pair<List<I>, List<O>> inputsOutputs = this.getInputsOutputsRule2(basisState, input);
        if (inputsOutputs == null || inputsOutputs.first() == null || inputsOutputs.second() == null) {
            throw new RuntimeException("Something is wrong!");
        }
        this.obsTool.insertObservation(inputsOutputs.first(), inputsOutputs.second());
    }

    private Pair<List<I>, List<O>> getInputsOutputsRule2(Node<I, O> basisState, I input) {
        boolean updateR2Ads = (double) this.basis.size() >= 1.1 * (double) previousBasisSize;
        if (updateR2Ads) {
            this.previousBasisSize = this.basis.size();
        }

        if (rule2.equals(Rule2.Ads)) {
            Ads<I, O> suffix = this.getAds(this.getInitialAdsSet());
            return this.adaptiveOutputQuery(this.obsTool.getAccessSequence(basisState), input, suffix);
        }

        Pair<Node<I, O>, Node<I, O>> twoApartBasis = this.getTwoApartInGroup(this.basis);

        if (rule2.equals(Rule2.Nothing) || rule2.equals(Rule2.SepSeq) && twoApartBasis == null) {
            List<I> inputs = this.obsTool.getAccessSequence(basisState);
            inputs.add(input);
            List<O> outputs = this.outputOracle.answerQuery(inputs);
            return new Pair<>(inputs, outputs);
        }
        if (rule2.equals(Rule2.SepSeq)) {
            Node<I, O> basisOne = twoApartBasis.first();
            Node<I, O> basisTwo = twoApartBasis.second();
            List<I> witness = getOrComputeWitness(basisOne, basisTwo);
            List<I> inputs = this.obsTool.getAccessSequence(basisState);
            inputs.add(input);
            inputs.addAll(witness);
            List<O> outputs = this.outputOracle.answerQuery(inputs);
            return new Pair<>(inputs, outputs);
        }
        return null;
    }

    private Ads<I, O> getAds(HashSet<Node<I, O>> block) {
        if (this.cache && this.adsCache.containsKey(block)) {
            Ads<I, O> suffix = this.adsCache.get(block);
            suffix.resetToRoot();
            return suffix;
        }
        Ads<I, O> suffix = new Ads<>(this.obsTool, block, cache);
        if (this.cache) {
            this.adsCache.put(block, suffix);
        }
        return suffix;
    }

    private Pair<List<I>, List<O>> adaptiveOutputQuery(List<I> prefix, I infix, Ads<I, O> suffix) {
        List<I> newPrefix = new ArrayList<>(prefix);
        newPrefix.add(infix);
        return this.adaptiveOutputQuery(newPrefix, suffix);
    }

    private Pair<List<I>, List<O>> adaptiveOutputQuery(List<I> prefix, Ads<I, O> suffix) {
        Node<I, O> fromNode = this.obsTool.getSuccessor(prefix);
        if (fromNode != null) {
            Pair<List<I>, List<O>> treeReply = this.answerAdsFromTree(suffix, fromNode);
            suffix.resetToRoot();
            if (treeReply != null) {
                List<I> inputs = new ArrayList<>(prefix);
                List<O> outputs = this.obsTool.getObservation(prefix);
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
        this.obsTool.insertObservation(inputs, outputs);

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

    protected void updateFrontierAndBasis() {
        this.updateGraphEdges();
        this.applyRuleOne();
        this.makeFrontierConsistentWithBasis();
        this.updateGraphEdges();
    }

    protected void updateGraphEdges() {
        for (Node<I, O> node : this.apartnessGraph.keySet()) {
            this.updateNodeEdges(node);
        }
    }

    protected void applyRuleOne() {
        Node<I, O> newBasis = this.findIsolatedFrontier();
        if (newBasis == null) return;

        this.frontiers.remove(newBasis);
        this.basis.add(newBasis);
        this.updateGraphAfterPromoteFrontier(newBasis);
    }

    protected void makeFrontierConsistentWithBasis() {
        for (Node<I, O> basisNode : this.basis) {
            for (I input : basisNode.getSuccessors().keySet()) {
                Node<I, O> frontierMaybe = basisNode.getSuccessor(input);
                if (frontierMaybe == null || this.basis.contains(frontierMaybe) || this.frontiers.contains(frontierMaybe)){
                    continue;
                }
                if (shouldFrontierAddToApartnessGraph(basisNode, input)) {
                    this.frontiers.add(frontierMaybe);
                    this.addNodeToGraph(frontierMaybe);
                }
            }
        }
    }

    protected List<I> getOrComputeWitness(Node<I, O> nodeOne, Node<I, O> nodeTwo) {
        Pair<Node<I, O>, Node<I, O>> pairOne = new Pair<>(nodeOne, nodeTwo);
        if (this.witnessCache.containsKey(pairOne)) {
            return this.witnessCache.get(pairOne);
        }
        Pair<Node<I, O>, Node<I, O>> pairTwo = new Pair<>(nodeTwo, nodeOne);
        if (this.witnessCache.containsKey(pairTwo)) {
            return this.witnessCache.get(pairTwo);
        }
        List<I> witness = Apartness.computeWitness(nodeOne, nodeTwo, this.obsTool);
        if (witness != null) {
            this.witnessCache.put(pairOne, witness);
            this.witnessCache.put(pairTwo, witness);
        }
        return witness;
    }

    public abstract void processBinarySearch(CompactMealy<I, O> hypothesis, List<I> ceInputs, List<O> ceOutputs);
    protected abstract void addNodeToGraph(Node<I, O> node);
    protected abstract Node<I, O> findIsolatedFrontier();
    protected abstract void updateGraphAfterPromoteFrontier(Node<I, O> newBasis);
    protected abstract void updateNodeEdges(Node<I, O> node);
    protected abstract boolean isBasisMustExploreWithInput(Node<I, O> basisNode, I input);
    protected abstract Pair<Node<I, O>, Node<I, O>> getTwoApartInGroup(HashSet<Node<I, O>> group);
    protected abstract boolean isStateIdentified(Node<I, O> state);
    protected abstract boolean hasOnlyOneApartPairNeighbours(Node<I, O> state);
    protected abstract HashSet<Node<I, O>> getInitialAdsSet();
    protected abstract boolean isBasisNodeComplete(Node<I, O> basisNode);
    protected abstract CompactMealy<I, O> constructHypothesis();
    protected abstract List<I> findCounterExample(CompactMealy<I, O> hypothesis);
    protected abstract boolean shouldFrontierAddToApartnessGraph(Node<I, O> bNode, I input);
}
