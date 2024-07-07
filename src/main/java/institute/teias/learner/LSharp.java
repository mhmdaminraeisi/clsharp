package institute.teias.learner;

import com.google.common.collect.HashBiMap;
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
    private final HashSet<I> alphabet;
    private final ObservationTree<I, O> observationTree;
    private final HashSet<Node<I, O>> basis = new HashSet<>();
    private final HashMap<Node<I, O>, HashSet<Node<I, O>>> frontierToBasisMap = new HashMap<>();
    private final HashMap<Pair<Node<I, O>, Node<I, O>>, List<I>> witnessCache = new HashMap<>();
    private final HashBiMap<Node<I, O>, Integer> basisToStateMap = HashBiMap.create();

    public LSharp(OutputOracle<I, O> outputOracle, TestOracle<I, O> testOracle, HashSet<I> alphabet) {
        this.outputOracle = outputOracle;
        this.testOracle = testOracle;
        this.alphabet = alphabet;
        this.observationTree = new NormalObservationTree<>(this.alphabet);
        this.basis.add(this.observationTree.getRoot());
    }

    public CompactMealy<I, O> learnMealy() {
        int round = 1;
        while (true) {
            CompactMealy<I, O> hypothesis = this.buildHypothesis();
            System.out.println("Round : " + round);
            System.out.println(this.basis.size() + "  " + hypothesis.size());
//            System.out.println(this);
            Pair<List<I>, List<O>> ce = this.testOracle.findCounterExample(hypothesis);
            if (ce == null) {
                System.out.println("finished in " + round + " rounds.");
                return hypothesis;
            };
//            System.out.println("CE = " + ce.first() + " | " + ce.second());
            this.processCounterExample(hypothesis, ce.first(), ce.second());
            round += 1;
        }
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
        Iterator<O> ceIterator = ceOutputs.iterator();
        Iterator<O> hypothesisIterator = hypothesisOutputs.iterator();

        int index = 0;
        while (ceIterator.hasNext() && hypothesisIterator.hasNext()) {
            O ceOutput = ceIterator.next();
            O hypOutput = hypothesisIterator.next();
            if (!ceOutput.equals(hypOutput)) return index;
            index += 1;
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
        HashSet<Node<I, O>> basisCandidates = this.frontierToBasisMap.get(frontierState);
        this.updateBasisCandidates(frontierState);

        int previousCandidatesSize = basisCandidates.size();
        if (previousCandidatesSize < 2) return;
        // TODO Add Ads implementations
        Iterator<Node<I, O>> iterator = basisCandidates.iterator();
        Node<I, O> basisOne = iterator.next();
        Node<I, O> basisTwo = iterator.next();

        List<I> witness = getOrComputeWitness(basisOne, basisTwo);
        List<I> inputs = this.observationTree.getAccessSequence(frontierState);
        inputs.addAll(witness);
        List<O> outputs = this.outputOracle.answerQuery(inputs);

        this.observationTree.insertObservation(frontierState, witness, outputs.subList(outputs.size()-witness.size(), outputs.size()));
        this.updateBasisCandidates(frontierState);

        if (this.frontierToBasisMap.get(frontierState).size() >= previousCandidatesSize) {
            throw new RuntimeException("Did not increase norm.");
        }
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
        // TODO Add ADS and SepSeq implementations.
        List<I> inputs = this.observationTree.getAccessSequence(basisState);
        inputs.add(input);
        List<O> outputs = this.outputOracle.answerQuery(inputs);
        this.observationTree.insertOneTransition(basisState, input, outputs.getLast());
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
}
