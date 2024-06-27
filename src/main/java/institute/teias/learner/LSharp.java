package institute.teias.learner;

import institute.teias.obsTree.Node;
import institute.teias.obsTree.NormalObservationTree;
import institute.teias.obsTree.ObservationTree;
import institute.teias.oracles.OutputOracle;
import institute.teias.oracles.TestOracle;
import institute.teias.utils.Pair;
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

    public LSharp(OutputOracle<I, O> outputOracle, TestOracle<I, O> testOracle, HashSet<I> alphabet) {
        this.outputOracle = outputOracle;
        this.testOracle = testOracle;
        this.alphabet = alphabet;
        this.observationTree = new NormalObservationTree<>(this.alphabet);
        this.basis.add(this.observationTree.getRoot());
    }

    public CompactMealy<I, O> buildHypothesis() {
        return null;
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
