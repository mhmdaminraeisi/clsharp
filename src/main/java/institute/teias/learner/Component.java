package institute.teias.learner;

import institute.teias.io.FileManager;
import institute.teias.obsTree.Node;
import institute.teias.obsTree.ObservationForest;
import institute.teias.oracles.OutputOracle;
import institute.teias.utils.Pair;
import lombok.Getter;
import lombok.Setter;
import net.automatalib.alphabet.Alphabet;
import net.automatalib.alphabet.Alphabets;
import net.automatalib.automaton.transducer.CompactMealy;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class Component<I, O> {
    private final OutputOracle<I, O> outputOracle;
    @Getter private final HashSet<I> alphabet;
    @Getter private final ObservationForest<I, O> observationForest;
    private final HashMap<Pair<Node<I, O>, Node<I, O>>, List<I>> witnessCache = new HashMap<>();
    @Getter private final HashSet<Node<I, O>> frontiers = new HashSet<>();
    @Getter private final HashSet<Node<I, O>> basis = new HashSet<>();
    @Getter private final HashMap<Node<I, O>, HashSet<Node<I, O>>> edges = new HashMap<>();
    private final HashMap<Node<I, O>, Integer> basisToState = new HashMap<>();
    private final HashMap<Integer, HashSet<Node<I, O>>> stateToBasis = new HashMap<>();

    public Component(OutputOracle<I, O> outputOracle, HashSet<I> alphabet) {
        this.outputOracle = outputOracle;
        this.alphabet = alphabet;
        this.observationForest = new ObservationForest<>(alphabet);

        this.basis.addAll(this.observationForest.getRoots().values());

        for (Node<I, O> root : this.observationForest.getRoots().values()) {
            HashSet<Node<I, O>> rootEdges = new HashSet<>(this.observationForest.getRoots().values());
            rootEdges.remove(root);
            this.edges.put(root, rootEdges);
        }
    }

    public void mergeWithComponents(List<Component<I, O>> otherComponents, List<I> ceInputs, List<O> ceOutputs) {
        for (Component<I, O> component : otherComponents) {
            this.alphabet.addAll(component.getAlphabet());
            this.observationForest.mergeWithAnotherForest(component.getObservationForest());
        }
        this.observationForest.insertObservation(ceInputs, ceOutputs);

        for (Component<I, O> component : otherComponents) {
            this.basis.addAll(component.getBasis());
            this.frontiers.addAll(component.getFrontiers());

            HashSet<Node<I, O>> leftNodes = new HashSet<>(this.edges.keySet());
            HashSet<Node<I, O>> rightNodes = new HashSet<>(component.getEdges().keySet());
            leftNodes.removeAll(this.observationForest.getRoots().values());
            rightNodes.removeAll(component.getObservationForest().getRoots().values());

            this.edges.putAll(component.getEdges());
            for (Node<I, O> l : leftNodes) {
                for (Node<I, O> r : rightNodes) {
                    this.edges.get(l).add(r);
                    this.edges.get(r).add(l);
                }
            }
        }

        for (Node<I, O> root : this.observationForest.getRoots().values()) {
            HashSet<Node<I, O>> rootEdges = new HashSet<>(this.observationForest.getRoots().values());
            rootEdges.remove(root);
            this.edges.get(root).addAll(rootEdges);
        }

        for (Node<I, O> fNode : this.frontiers) {
            boolean isConnectedToAnyRoot = false;
            for (Node<I, O> e : this.edges.get(fNode)) {
                if (this.observationForest.getRoots().containsValue(e)) {
                    isConnectedToAnyRoot = true;
                    break;
                }
            }
            if (isConnectedToAnyRoot) {
                for (Node<I, O> root : this.observationForest.getRoots().values()) {
                    this.edges.get(root).add(fNode);
                    this.edges.get(fNode).add(root);
                }
            }
        }
    }

    public CompactMealy<I, O> buildHypothesis() {
        while (true) {
            this.makeComponentAdequate();
            CompactMealy<I, O> hypothesis = this.constructHypothesis();
            // check consistency
            List<I> counterExample = Apartness.computeWitnessInForestAndHypothesis(this.observationForest, hypothesis);
            // ToDo last ce consistency!
            if (counterExample == null) return hypothesis;
            List<O> ceOutputs = this.observationForest.getObservation(counterExample);
            this.processCounterExample(hypothesis, counterExample, ceOutputs);
        }
    }

    public void processCounterExample(CompactMealy<I, O> hypothesis, List<I> ceInputs, List<O> ceOutputs) {
        // ToDo clear rule caches.
        this.observationForest.insertObservation(ceInputs, ceOutputs);
        List<O> hypothesisOutputs = hypothesis.computeOutput(ceInputs).asList();
        int prefixIndex = this.getCounterExamplePrefixIndex(ceOutputs, hypothesisOutputs);
        this.processBinarySearch(hypothesis, ceInputs.subList(0, prefixIndex), ceOutputs.subList(0, prefixIndex));
        // ToDo cache rules.
    }
    // Todo binary search!
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
        Node<I, O> r = this.observationForest.getSuccessor(ceInputs);
        this.updateFrontierAndBasis();
        if (this.frontiers.contains(r) || this.basis.contains(r)) {
            return;
        }
        Integer q_state = hypothesis.getSuccessor(hypothesis.getInitialState(), ceInputs);
        Node<I, O> q = this.getBasisInStateApartFromNode(r, q_state);
        if (q == null) {
            throw new RuntimeException("q must not be null.");
        }

        List<I> prefix = new ArrayList<>();
        Node<I, O> currentNode = this.observationForest.getRoot(ceInputs);
        for (I input : ceInputs) {
            if (this.frontiers.contains(currentNode)) break;
            currentNode = currentNode.getSuccessor(input);
            prefix.add(input);
        }

        int h = (prefix.size() + ceInputs.size()) / 2;
        List<I> sigma1 = ceInputs.subList(0, h);
        List<I> sigma2 = ceInputs.subList(h, ceOutputs.size());

        Integer q_p_state = hypothesis.getSuccessor(hypothesis.getInitialState(), sigma1);
        Node<I, O> q_p = this.stateToBasis.get(q_p_state).iterator().next();
        List<I> q_p_access = this.observationForest.getAccessSequence(q_p);

        List<I> witness = this.getOrComputeWitness(r, q);
        if (witness == null) {
            throw new RuntimeException("Witness must not be null.");
        }

        List<I> queryInputs = new ArrayList<>(q_p_access);
        queryInputs.addAll(sigma2);
        queryInputs.addAll(witness);
        List<O> queryOutputs = this.outputOracle.answerQuery(queryInputs);
        this.observationForest.insertObservation(queryInputs, queryOutputs);

        Node<I, O> r_p = this.observationForest.getSuccessor(sigma1);
        List<I> witness_p = Apartness.computeWitnessInForest(r_p, q_p, this.observationForest);
        if (witness_p != null) {
            this.processBinarySearch(hypothesis, sigma1, ceOutputs.subList(0, h));
        } else {
            List<I> newInputs = new ArrayList<>(q_p_access);
            newInputs.addAll(sigma2);
            this.processBinarySearch(hypothesis, newInputs, queryOutputs.subList(0, newInputs.size()));
        }
    }

    private Node<I, O> getBasisInStateApartFromNode(Node<I, O> node, Integer state) {
        for (Node<I, O> n : this.stateToBasis.get(state)) {
            if (this.getOrComputeWitness(n, node) != null) return n;
        }
        return null;
    }

    public CompactMealy<I, O> constructHypothesis() {
        Alphabet<I> hypothesisAlphabet = Alphabets.fromCollection(new TreeSet<>(this.alphabet));
        CompactMealy<I, O> hypothesis = new CompactMealy<>(hypothesisAlphabet);

        basisToState.clear();
        stateToBasis.clear();
        Node<I, O> root = this.observationForest.getRoots().values().iterator().next();
        basisToState.put(root, hypothesis.addState());
        stateToBasis.put(basisToState.get(root), new HashSet<>(List.of(root)));
        for (Node<I, O> e : this.edges.get(root)) {
            if (this.frontiers.contains(e)) continue;
            basisToState.put(e, basisToState.get(root));
            stateToBasis.get(basisToState.get(root)).add(e);
        }
        for (Node<I, O> basisNode : this.basis) {
            if (this.basisToState.containsKey(basisNode)) continue;
            basisToState.put(basisNode, hypothesis.addState());
            stateToBasis.put(basisToState.get(basisNode), new HashSet<>(List.of(basisNode)));
            for (Node<I, O> e : this.edges.get(basisNode)) {
                if (this.frontiers.contains(e)) continue;
                basisToState.put(e, basisToState.get(basisNode));
                stateToBasis.get(basisToState.get(basisNode)).add(e);
            }
        }

        hypothesis.setInitialState(basisToState.get(root));

        for (Node<I, O> basisNode : this.basis) {
            Integer source = basisToState.get(basisNode);
            for (I input : basisNode.getSuccessors().keySet()) {
                if (hypothesis.getTransition(source, input) != null) continue;

                O output = basisNode.getOutput(input);
                Node<I, O> successor = basisNode.getSuccessor(input);
                if (this.frontiers.contains(successor)) {
                    if (!isFrontierNodeConnectedToBasis(successor)) {
                        throw new RuntimeException("Frontier is not connected to basis.");
                    }
                    for (Node<I, O> e : this.edges.get(successor)) {
                        if (this.basis.contains(e)) {
                            successor = e;
                            break;
                        }
                    }
                }
                Integer destination = basisToState.get(successor);
                hypothesis.addTransition(source, input, destination, output);
            }
        }
        return hypothesis;
    }

    private void makeComponentAdequate() {
        this.updateFrontierAndBasis();
        while (!this.isComponentAdequate()) {
            this.makeAllGraphComponentsClique();
            this.makeBasisComplete();
            this.promoteFrontierState();
        }
    }

    private boolean isComponentAdequate() {
        this.makeFrontierConsistentWithBasis();

        // Check graph components are clique.
        for (Node<I, O> node : this.edges.keySet()) {
            if (this.getApartNeighbours(node) != null) return false;
        }
        // Check basis nodes are complete.
        for (Node<I, O> basisNode : this.basis) {
            if (!isBasisNodeComplete(basisNode)) return false;
        }
        return true;
    }

    private boolean isBasisNodeComplete(Node<I, O> basisNode) {
        for (I input : this.alphabet) {
            if (basisNode.getOutput(input) != null) continue;
            boolean isContainsInput = false;
            for (Node<I, O> e : this.edges.get(basisNode)) {
                if (this.basis.contains(e) && e.getOutput(input) != null) {
                    isContainsInput = true;
                    break;
                }
            }
            if (!isContainsInput) return false;
        }
        return true;
    }

    /** Applying Rule 2: Explores the frontier for all basis states. */
    private void makeBasisComplete() {
        for (Node<I, O> basisNode : this.basis) {
            for (I input : this.alphabet) {
                if (basisNode.getOutput(input) != null) continue;
                boolean mustExplore = true;
                for (Node<I, O> e : this.edges.get(basisNode)) {
                    if (this.basis.contains(e) && e.getSuccessor(input) != null) {
                        mustExplore = false;
                        break;
                    }
                }
                if (!mustExplore) continue;
                this.exploreFrontier(basisNode, input);
            }
        }
    }

    private void exploreFrontier(Node<I, O> basisNode, I input) {
        // TODO Add ADS and SepSeq implementations.
        List<I> inputs = this.observationForest.getAccessSequence(basisNode);
        inputs.add(input);
        List<O> outputs = this.outputOracle.answerQuery(inputs);
        this.observationForest.insertOneTransition(basisNode, input, outputs.getLast());
    }

    /** Applying Rule 0: Identify all group candidates. */
    private void makeAllGraphComponentsClique() {
        this.updateGraphEdges();
        while (true) {
            boolean isDone = true;
            for (Node<I, O> node : this.edges.keySet()) {
                this.updateNodeEdges(node);
                if (this.getApartNeighbours(node) != null) {
                    this.makeNodeComponentClique(node);
                    isDone = false;
                }
            }
            if (isDone) return;
        }
    }

    private void makeNodeComponentClique(Node<I, O> node) {
        if (this.edges.get(node).size() < 2) return;
        Pair<Node<I, O>, Node<I, O>> apartPair = this.getApartNeighbours(node);
        while (apartPair != null) {
            Node<I, O> bOne = apartPair.first();
            Node<I, O> bTwo = apartPair.second();
            List<I> witness = this.getOrComputeWitness(bOne, bTwo);
            if (witness == null) {
                throw new RuntimeException("Witness must not be null.");
            }
            List<I> inputs = this.observationForest.getAccessSequence(node);
            inputs.addAll(witness);
            
            List<O> outputs = this.outputOracle.answerQuery(inputs);
            this.observationForest.insertObservation(node, witness, outputs.subList(outputs.size()-witness.size(), outputs.size()));
            this.updateNodeEdges(node);
            apartPair = this.getApartNeighbours(node);
        }
    }

    private Pair<Node<I, O>, Node<I, O>> getApartNeighbours(Node<I, O> node) {
        for (Node<I, O> bOne : this.edges.get(node)) {
            for (Node<I, O> bTwo : this.edges.get(node)) {
                if (bOne == bTwo) continue;
                if (this.getOrComputeWitness(bOne, bTwo) != null) {
                    return new Pair<>(bOne, bTwo);
                }
            }
        }
        return null;
    }

    private void updateFrontierAndBasis() {
        this.updateGraphEdges();
        this.promoteFrontierState();
        this.makeFrontierConsistentWithBasis();
        this.updateGraphEdges();
    }

    private void updateGraphEdges() {
        for (Node<I, O> node : this.edges.keySet()) {
            this.updateNodeEdges(node);
        }
    }

    private void updateNodeEdges(Node<I, O> node) {
        HashSet<Node<I, O>> nodeEdges = new HashSet<>(this.edges.get(node));
        boolean clearRoots = false;
        for (Node<I, O> n : nodeEdges) {
            if (this.getOrComputeWitness(node, n) != null) {
                if (this.observationForest.getRoots().containsValue(n)) {
                    clearRoots = true;
                }
                this.edges.get(node).remove(n);
                this.edges.get(n).remove(node);
            }
        }
        if (clearRoots) {
            for (Node<I, O> root : this.observationForest.getRoots().values()) {
                this.edges.get(node).remove(root);
                this.edges.get(root).remove(node);
            }
        }
    }

    private void promoteFrontierState() {
        Node<I, O> newBasis = null;
        for (Node<I, O> fNode : this.frontiers) {
            if (!isFrontierNodeConnectedToBasis(fNode)) {
                newBasis = fNode;
                break;
            }
        }
        if (newBasis == null) return;

        this.frontiers.remove(newBasis);
        this.basis.add(newBasis);
    }

    private boolean isFrontierNodeConnectedToBasis(Node<I, O> frontierNode) {
        for (Node<I, O> e : this.edges.get(frontierNode)) {
            if (this.basis.contains(e)) return true;
        }
        return false;
    }

    private void makeFrontierConsistentWithBasis() {
        for (Node<I, O> basisNode : this.basis) {
            for (I input : basisNode.getSuccessors().keySet()) {
                Node<I, O> frontierMaybe = basisNode.getSuccessor(input);
                if (frontierMaybe == null || this.basis.contains(frontierMaybe) || this.frontiers.contains(frontierMaybe)){
                    continue;
                }
                this.frontiers.add(frontierMaybe);
                this.addNodeToGraph(frontierMaybe);
            }
        }
    }

    private void addNodeToGraph(Node<I, O> node) {
        this.edges.put(node, new HashSet<>());
        for (Node<I, O> n : this.edges.keySet()) {
            if (n == node) continue;
            if (this.getOrComputeWitness(n, node) == null) {
                this.edges.get(node).add(n);
                this.edges.get(n).add(node);
            }
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
        List<I> witness = Apartness.computeWitnessInForest(nodeOne, nodeTwo, this.observationForest);
        if (witness != null) {
            this.witnessCache.put(pairOne, witness);
            this.witnessCache.put(pairTwo, witness);
        }
        return witness;
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder();
        buffer.append("##################################################\n");
        buffer.append("observation tree: \n");
        buffer.append(observationForest.toString());
        buffer.append("\n");
        buffer.append("basis: \n");
        for (Node<I, O> b : this.basis) {
            if (b.getInputOutput() != null) {
                buffer.append(b.getInputOutput().first()).append("|").append(b.getInputOutput().second());
            }
            buffer.append(this.observationForest.getAccessSequence(b)).append(" : {");
            for (Node<I, O> n : this.edges.get(b)) {
                buffer.append(this.observationForest.getAccessSequence(n)).append(" - ");
            }
            buffer.append("}\n");
        }
        buffer.append("\n").append("frontier: \n");
        for (Node<I, O> f : this.frontiers) {
            buffer.append(this.observationForest.getAccessSequence(f)).append("  :  ");
            buffer.append("{");
            for (Node<I, O> b : this.edges.get(f)) {
                buffer.append(this.observationForest.getAccessSequence(b)).append(" - ");
            }
            buffer.append("}");
            buffer.append("\n");
        }
        buffer.append("____________________________________________________\n");
        return buffer.toString();
    }
}
