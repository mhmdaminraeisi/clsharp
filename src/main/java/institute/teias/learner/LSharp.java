package institute.teias.learner;

import com.google.common.collect.HashBiMap;
import institute.teias.obsTree.Node;
import institute.teias.obsTree.ObservationTree;
import institute.teias.oracles.OutputOracle;
import institute.teias.oracles.TestOracle;
import institute.teias.oracles.perfect.PerfectTestOracle;
import institute.teias.utils.Pair;
import net.automatalib.alphabet.Alphabet;
import net.automatalib.alphabet.Alphabets;
import net.automatalib.automaton.transducer.CompactMealy;

import java.util.*;
import java.util.stream.Collectors;

public class LSharp<I, O> extends BaseLSharp<I, O> {
    private final ObservationTree<I, O> observationTree;
    private final TestOracle<I, O> testOracle;
    private final PerfectTestOracle<I, O> perfectOracle;
    private final HashBiMap<Node<I, O>, Integer> basisToStateMap = HashBiMap.create();

    public LSharp(
            OutputOracle<I, O> outputOracle,
            TestOracle<I, O> testOracle,
            PerfectTestOracle<I, O> perfectOracle,
            HashSet<I> alphabet,
            Rule2 rule2,
            Rule3 rule3,
            boolean cache
    ) {
        super(outputOracle, alphabet, rule2, rule3, cache);
        this.observationTree = new ObservationTree<>(this.alphabet);
        this.setObsTool(this.observationTree);
        this.testOracle = testOracle;
        this.perfectOracle = perfectOracle;

        this.basis.add(this.observationTree.getRoot());
    }

    public LearnResult<I, O> learnMealy() {
        int round = 0;
        while (true) {
            round += 1;
            CompactMealy<I, O> hypothesis = this.buildHypothesis();
            Pair<List<I>, List<O>> idealCe = this.perfectOracle.findCounterExample(hypothesis, this.alphabet);
            if (idealCe == null) return this.createResult(true, round, hypothesis);
            Pair<List<I>, List<O>> ce = this.testOracle.findCounterExample(hypothesis, this.alphabet);
            if (ce == null) {
                System.out.println("No CE found!");
                return this.createResult(false, round, hypothesis);
            };
            this.processCounterExample(hypothesis, ce.first(), ce.second());
        }
    }

    private LearnResult<I, O> createResult(boolean isLearned, int rounds, CompactMealy<I, O> hypothesis) {
        return new LearnResult<>(
                outputOracle.getSymbolsCount(),
                outputOracle.getResetsCount(),
                testOracle.getSymbolsCount(),
                testOracle.getResetsCount(),
                rounds,
                1,
                0,
                0,
                isLearned,
                hypothesis,
                hypothesis.size()
        );
    }

    public void processBinarySearch(CompactMealy<I, O> hypothesis, List<I> ceInputs, List<O> ceOutputs) {
        Node<I, O> r = this.observationTree.getSuccessor(ceInputs);
        this.updateFrontierAndBasis();
        if (this.basis.contains(r) || this.frontiers.contains(r) || ceInputs.isEmpty()) {
            return;
        }
        Integer q_state = hypothesis.getSuccessor(hypothesis.getInitialState(), ceInputs);
        Node<I, O> q = this.basisToStateMap.inverse().get(q_state);
        if (q == null) {
            throw new RuntimeException("q must not be null.");
        }

        List<I> witness = this.getOrComputeWitness(r, q);
        if (witness == null) {
            throw new RuntimeException("Witness must not be null.");
        }

        List<I> prefix = new ArrayList<>();
        Node<I, O> currentNode = this.observationTree.getRoot();
        for (I input : ceInputs) {
            if (this.frontiers.contains(currentNode)) break;
            currentNode = currentNode.getSuccessor(input);
            prefix.add(input);
        }

        int h = (prefix.size() + ceInputs.size()) / 2;
        List<I> sigma1 = ceInputs.subList(0, h);
        List<I> sigma2 = ceInputs.subList(h, ceOutputs.size());

        Node<I, O> r_p = this.observationTree.getSuccessor(sigma1);
        Integer q_p_state = hypothesis.getSuccessor(hypothesis.getInitialState(), sigma1);
        Node<I, O> q_p = this.basisToStateMap.inverse().get(q_p_state);
        List<I> q_p_access = this.observationTree.getAccessSequence(q_p);

        List<I> queryInputs = new ArrayList<>(q_p_access);
        queryInputs.addAll(sigma2);
        queryInputs.addAll(witness);
        List<O> queryOutputs = this.outputOracle.answerQuery(queryInputs);
        this.observationTree.insertObservation(queryInputs, queryOutputs);

        List<I> witness_p = Apartness.computeWitness(r_p, q_p, this.observationTree);
        if (witness_p != null) {
            this.processBinarySearch(hypothesis, sigma1, ceOutputs.subList(0, h));
        } else {
            List<I> newInputs = new ArrayList<>(q_p_access);
            newInputs.addAll(sigma2);
            this.processBinarySearch(hypothesis, newInputs, queryOutputs.subList(0, newInputs.size()));
        }
    }
    
    @Override
    protected List<I> findCounterExample(CompactMealy<I, O> hypothesis) {
        return Apartness.computeWitnessInTreeAndHypothesis(this.observationTree, hypothesis);
    }

    @Override
    public CompactMealy<I, O> constructHypothesis() {
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
                if (this.apartnessGraph.containsKey(successor)) {
                    HashSet<Node<I, O>> candidates = this.apartnessGraph.get(successor);
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

    @Override
    protected boolean isBasisNodeComplete(Node<I, O> basisNode) {
        for (I input : this.alphabet) {
            if (basisNode.getOutput(input) == null) return false;
        }
        return true;
    }

    @Override
    protected boolean hasOnlyOneApartPairNeighbours(Node<I, O> state) {
        return this.apartnessGraph.get(state).size() == 2;
    }

    @Override
    protected boolean isStateIdentified(Node<I, O> state) {
        return this.apartnessGraph.get(state).size() == 1;
    }

    @Override
    protected HashSet<Node<I, O>> getInitialAdsSet() {
        return new HashSet<>(this.basis);
    }

    @Override
    protected Pair<Node<I, O>, Node<I, O>> getTwoApartInGroup(HashSet<Node<I, O>> group) {
        if (group.size() <= 1) return null;
        Iterator<Node<I, O>> iterator = group.iterator();
        Node<I, O> basisOne = iterator.next();
        Node<I, O> basisTwo = iterator.next();
        return new Pair<>(basisOne, basisTwo);

    }

    @Override
    protected boolean isBasisMustExploreWithInput(Node<I, O> basisNode, I input) {
        return basisNode.getSuccessor(input) == null;
    }

    @Override
    protected void updateNodeEdges(Node<I, O> frontierState) {
        this.apartnessGraph.replace(frontierState, this.apartnessGraph.get(frontierState)
                .stream().filter(b -> !Apartness.statesAreApart(frontierState, b))
                .collect(Collectors.toCollection(HashSet::new)));
    }

    @Override
    protected void updateGraphAfterPromoteFrontier(Node<I, O> newBasis) {
        this.apartnessGraph.keySet().remove(newBasis);
        this.apartnessGraph.replaceAll((f, basisCandidates) -> {
            if (!Apartness.statesAreApart(f, newBasis)) {
                basisCandidates.add(newBasis);
            }
            return basisCandidates;
        });
    }

    @Override
    protected Node<I, O> findIsolatedFrontier() {
        for (Map.Entry<Node<I, O>, HashSet<Node<I, O>>> e : this.apartnessGraph.entrySet()) {
            if (e.getValue().isEmpty()) return e.getKey();
        }
        return null;
    }

    @Override
    protected boolean shouldFrontierAddToApartnessGraph(Node<I, O> bNode, I input) {
        return true;
    }

    @Override
    protected void addNodeToGraph(Node<I, O> node) {
        HashSet<Node<I, O>> basisCandidates = this.basis.stream()
                .filter(b -> !Apartness.statesAreApart(node, b))
                .collect(Collectors.toCollection(HashSet::new));

        apartnessGraph.put(node, basisCandidates);
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder();
        buffer.append("##################################################\n");
        buffer.append("observation tree: \n");
        buffer.append(this.observationTree.toString());
        buffer.append("\n");
        buffer.append("basis: \n");
        for (Node<I, O> b : this.basis) {
            if (b.getInputOutput() != null) {
                buffer.append(b.getInputOutput().first()).append("|").append(b.getInputOutput().second());
            }
            buffer.append(this.observationTree.getAccessSequence(b)).append("\n");
        }
        buffer.append("\n").append("frontier: \n");
        for (Map.Entry<Node<I, O>, HashSet<Node<I, O>>> f : this.apartnessGraph.entrySet()) {
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
