package institute.teias.learner;

import institute.teias.obsTree.BaseObservation;
import institute.teias.obsTree.Node;
import institute.teias.obsTree.ObservationForest;
import institute.teias.oracles.OutputOracle;
import institute.teias.utils.Pair;
import lombok.Getter;
import net.automatalib.alphabet.Alphabet;
import net.automatalib.alphabet.Alphabets;
import net.automatalib.automaton.transducer.CompactMealy;

import java.util.*;

public class Component<I, O> extends BaseLSharp<I, O> {
    @Getter private final ObservationForest<I, O> observationForest;
    private final HashMap<Node<I, O>, Integer> basisToState = new HashMap<>();
    private final HashMap<Integer, HashSet<Node<I, O>>> stateToBasis = new HashMap<>();
    
    public Component(
            OutputOracle<I, O> outputOracle,
            HashSet<I> alphabet,
            Rule2 rule2,
            Rule3 rule3,
            boolean cache
    ) {
        super(outputOracle, alphabet, rule2, rule3, cache);
        this.observationForest = new ObservationForest<>(alphabet);
        this.setObsTool(this.observationForest);

        this.basis.addAll(this.observationForest.getRootNodes());

        for (Node<I, O> root : this.observationForest.getRootNodes()) {
            HashSet<Node<I, O>> rootEdges = new HashSet<>(this.observationForest.getRootNodes());
            rootEdges.remove(root);
            this.apartnessGraph.put(root, rootEdges);
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

            HashSet<Node<I, O>> leftNodes = new HashSet<>(this.apartnessGraph.keySet());
            HashSet<Node<I, O>> rightNodes = new HashSet<>(component.getApartnessGraph().keySet());
            this.observationForest.getRootNodes().forEach(leftNodes::remove);
            component.getObservationForest().getRootNodes().forEach(rightNodes::remove);

            this.apartnessGraph.putAll(component.getApartnessGraph());
            for (Node<I, O> l : leftNodes) {
                for (Node<I, O> r : rightNodes) {
                    this.apartnessGraph.get(l).add(r);
                    this.apartnessGraph.get(r).add(l);
                }
            }
        }

        for (Node<I, O> root : this.observationForest.getRootNodes()) {
            HashSet<Node<I, O>> rootEdges = new HashSet<>(this.observationForest.getRootNodes());
            rootEdges.remove(root);
            this.apartnessGraph.get(root).addAll(rootEdges);
        }

        for (Node<I, O> fNode : this.frontiers) {
            boolean isConnectedToAnyRoot = false;
            for (Node<I, O> e : this.apartnessGraph.get(fNode)) {
                if (this.observationForest.getRootNodes().contains(e)) {
                    isConnectedToAnyRoot = true;
                    break;
                }
            }
            if (isConnectedToAnyRoot) {
                for (Node<I, O> root : this.observationForest.getRootNodes()) {
                    this.apartnessGraph.get(root).add(fNode);
                    this.apartnessGraph.get(fNode).add(root);
                }
            }
        }
    }

    public void processBinarySearch(CompactMealy<I, O> hypothesis, List<I> ceInputs, List<O> ceOutputs) {
        Node<I, O> r = this.observationForest.getSuccessor(ceInputs);
        this.updateFrontierAndBasis();
        if (this.basis.contains(r) || this.frontiers.contains(r) || ceInputs.isEmpty()) {
            return;
        }
        if (this.basis.contains(r.getParent())) {
            this.frontiers.add(r);
            this.addNodeToGraph(r);
            return;
        }
        Integer q_state = hypothesis.getSuccessor(hypothesis.getInitialState(), ceInputs);
        Node<I, O> q = this.getBasisStateApartFromNode(q_state, r);
        if (q == null) {
            throw new RuntimeException("q must not be null.");
        }

        List<I> witness = this.getOrComputeWitness(r, q);
        if (witness == null) {
            throw new RuntimeException("Witness must not be null.");
        }

        List<I> prefix = new ArrayList<>();
        Node<I, O> currentNode = this.observationForest.getRoot(ceInputs);
        for (I input : ceInputs) {
            if (currentNode.getParent() != null && !this.basis.contains(currentNode) && this.basis.contains(currentNode.getParent())) break;
            currentNode = currentNode.getSuccessor(input);
            prefix.add(input);
        }

        int h = (prefix.size() + ceInputs.size()) / 2;
        List<I> sigma1 = ceInputs.subList(0, h);
        List<I> sigma2 = ceInputs.subList(h, ceOutputs.size());


        Node<I, O> r_p = this.observationForest.getSuccessor(sigma1);
        Integer q_p_state = hypothesis.getSuccessor(hypothesis.getInitialState(), sigma1);
        Node<I, O> q_p = this.getBasisStateApartFromNodePrime(q_p_state, r_p);
        List<I> q_p_access = this.observationForest.getAccessSequence(q_p);

        List<I> queryInputs = new ArrayList<>(q_p_access);
        queryInputs.addAll(sigma2);
        queryInputs.addAll(witness);
        List<O> queryOutputs = this.outputOracle.answerQuery(queryInputs);
        this.observationForest.insertObservation(queryInputs, queryOutputs);

        q_p = this.getBasisStateApartFromNodePrime(q_p_state, r_p);
        List<I> witness_p = Apartness.computeWitness(r_p, q_p, this.observationForest);
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
        return Apartness.computeWitnessInForestAndHypothesis(this.observationForest, hypothesis);
    }

    private Node<I, O> getBasisStateApartFromNode(Integer q_state, Node<I, O> r) {
        for (Node<I, O> n : this.stateToBasis.get(q_state)) {
            if (this.getOrComputeWitness(n, r) != null) return n;
        }
        return null;
    }

    private Node<I, O> getBasisStateApartFromNodePrime(Integer q_p_state, Node<I, O> r_p) {
        Node<I, O> bState = this.getBasisStateApartFromNode(q_p_state, r_p);
        if (bState == null) {
            return this.stateToBasis.get(q_p_state).iterator().next();
        }
        return bState;
    }

    @Override
    public CompactMealy<I, O> constructHypothesis() {
        Alphabet<I> hypothesisAlphabet = Alphabets.fromCollection(new TreeSet<>(this.alphabet));
        CompactMealy<I, O> hypothesis = new CompactMealy<>(hypothesisAlphabet);

        basisToState.clear();
        stateToBasis.clear();
        Node<I, O> root = this.observationForest.getRootNodes().iterator().next();
        basisToState.put(root, hypothesis.addState());
        stateToBasis.put(basisToState.get(root), new HashSet<>(List.of(root)));
        for (Node<I, O> e : this.apartnessGraph.get(root)) {
            if (this.frontiers.contains(e)) continue;
            basisToState.put(e, basisToState.get(root));
            stateToBasis.get(basisToState.get(root)).add(e);
        }
        for (Node<I, O> basisNode : this.basis) {
            if (this.basisToState.containsKey(basisNode)) continue;
            basisToState.put(basisNode, hypothesis.addState());
            stateToBasis.put(basisToState.get(basisNode), new HashSet<>(List.of(basisNode)));
            for (Node<I, O> e : this.apartnessGraph.get(basisNode)) {
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
                if (!this.basis.contains(successor) && !this.frontiers.contains(successor)) {
                    continue;
                };
                if (this.frontiers.contains(successor)) {
                    if (isFrontierNodeDisconnectedFromBasis(successor)) {
                        throw new RuntimeException("Frontier is not connected to basis.");
                    }
                    for (Node<I, O> e : this.apartnessGraph.get(successor)) {
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

    @Override
    protected boolean isBasisNodeComplete(Node<I, O> basisNode) {
        for (I input : this.alphabet) {
            if (basisNode.getOutput(input) != null) continue;
            boolean isContainsInput = false;
            for (Node<I, O> e : this.apartnessGraph.get(basisNode)) {
                if (this.basis.contains(e) && e.getOutput(input) != null) {
                    isContainsInput = true;
                    break;
                }
            }
            if (!isContainsInput) return false;
        }
        return true;
    }

    @Override
    protected boolean hasOnlyOneApartPairNeighbours(Node<I, O> state) {
        int count = 0;
        for (Node<I, O> bOne : this.apartnessGraph.get(state)) {
            for (Node<I, O> bTwo : this.apartnessGraph.get(state)) {
                if (bOne == bTwo) continue;
                if (this.getOrComputeWitness(bOne, bTwo) != null) count += 1;
                if (count > 2) return false;
            }
        }
        return count == 2;
    }

    @Override
    protected boolean isStateIdentified(Node<I, O> state) {
        return this.getTwoApartInGroup(this.apartnessGraph.get(state)) == null;
    }

    @Override
    protected HashSet<Node<I, O>> getInitialAdsSet() {
        return new HashSet<>(this.apartnessGraph.keySet());
    }

    @Override
    protected Pair<Node<I, O>, Node<I, O>> getTwoApartInGroup(HashSet<Node<I, O>> group) {
        for (Node<I, O> bOne : group) {
            for (Node<I, O> bTwo : group) {
                if (bOne == bTwo) continue;
                if (this.getOrComputeWitness(bOne, bTwo) != null) {
                    return new Pair<>(bOne, bTwo);
                }
            }
        }
        return null;
    }

    @Override
    protected boolean isBasisMustExploreWithInput(Node<I, O> basisNode, I input) {
        if (basisNode.getSuccessor(input) != null) return false;

        for (Node<I, O> e : this.apartnessGraph.get(basisNode)) {
            if (this.basis.contains(e) && e.getSuccessor(input) != null) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void updateNodeEdges(Node<I, O> node) {
        HashSet<Node<I, O>> nodeEdges = new HashSet<>(this.apartnessGraph.get(node));

        boolean isRootNode = this.observationForest.getRoots().containsValue(node);
        for (Node<I, O> n : nodeEdges) {
            if (this.getOrComputeWitness(node, n) != null) {
                this.apartnessGraph.get(node).remove(n);
                this.apartnessGraph.get(n).remove(node);
                if (isRootNode) {
                    for (Node<I, O> root : this.observationForest.getRootNodes()) {
                        this.apartnessGraph.get(n).remove(root);
                        this.apartnessGraph.get(root).remove(n);
                    }
                } else if (this.observationForest.getRootNodes().contains(n)) {
                    for (Node<I, O> root : this.observationForest.getRootNodes()) {
                        this.apartnessGraph.get(node).remove(root);
                        this.apartnessGraph.get(root).remove(node);
                    }
                }
            }
        }
    }


    @Override
    protected void updateGraphAfterPromoteFrontier(Node<I, O> newBasis) {
        // Nothing To do.
    }

    @Override
    protected Node<I, O> findIsolatedFrontier() {
        for (Node<I, O> fNode : this.frontiers) {
            if (isFrontierNodeDisconnectedFromBasis(fNode)) {
                return fNode;
            }
        }
        return null;
    }

    private boolean isFrontierNodeDisconnectedFromBasis(Node<I, O> frontierNode) {
        for (Node<I, O> e : this.apartnessGraph.get(frontierNode)) {
            if (this.basis.contains(e)) return false;
        }
        return true;
    }

    @Override
    protected boolean shouldFrontierAddToApartnessGraph(Node<I, O> bNode, I input) {
        for (Node<I, O> b : this.apartnessGraph.get(bNode)) {
            if (this.basis.contains(b) && b.getSuccessor(input) != null) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void addNodeToGraph(Node<I, O> node) {
        this.apartnessGraph.put(node, new HashSet<>());

        boolean clearRoots = false;

        for (Node<I, O> n : this.apartnessGraph.keySet()) {
            if (n == node) continue;
            if (this.getOrComputeWitness(n, node) == null) {
                this.apartnessGraph.get(node).add(n);
                this.apartnessGraph.get(n).add(node);
            } else if (this.observationForest.getRootNodes().contains(n)) {
                clearRoots = true;
            }
        }

        if (clearRoots) {
            for (Node<I, O> root : this.observationForest.getRootNodes()) {
                this.apartnessGraph.get(node).remove(root);
                this.apartnessGraph.get(root).remove(node);
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder();
        buffer.append("##################################################\n");
        buffer.append("observation forest: \n");
        buffer.append(observationForest.toString());
        buffer.append("\n");
        buffer.append("basis: \n");
        for (Node<I, O> b : this.basis) {
            if (b.getInputOutput() != null) {
                buffer.append(b.getInputOutput().first()).append("|").append(b.getInputOutput().second());
            }
            buffer.append(this.observationForest.getAccessSequence(b)).append(" : {");
            for (Node<I, O> n : this.apartnessGraph.get(b)) {
                buffer.append(this.observationForest.getAccessSequence(n)).append(" - ");
            }
            buffer.append("}\n");
        }
        buffer.append("\n").append("frontier: \n");
        for (Node<I, O> f : this.frontiers) {
            buffer.append(this.observationForest.getAccessSequence(f)).append("  :  ");
            buffer.append("{");
            for (Node<I, O> b : this.apartnessGraph.get(f)) {
                buffer.append(this.observationForest.getAccessSequence(b)).append(" - ");
            }
            buffer.append("}");
            buffer.append("\n");
        }
        buffer.append("____________________________________________________\n");
        return buffer.toString();
    }
}
