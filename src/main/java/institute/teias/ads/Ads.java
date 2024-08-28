package institute.teias.ads;

import institute.teias.obsTree.Node;
import institute.teias.obsTree.ObservationTree;
import institute.teias.utils.Pair;

import java.util.*;
import java.util.HashSet;
import java.util.stream.Collectors;

public class Ads<I, O> implements IAds<I, O> {
    private AdsNode<I, O> currentNode;
    private final AdsNode<I, O> initialNode;

    public Ads(ObservationTree<I, O> obsTree, HashSet<Node<I, O>> currentBlock){
        this.initialNode = this.constructAds(obsTree, currentBlock);
        this.currentNode = initialNode;
    }

    public int getScore() {
        return this.initialNode.getScore();
    }
    
    private AdsNode<I, O> constructAds(ObservationTree<I, O> obsTree, HashSet<Node<I, O>> currentBlock) {
        if (currentBlock.size() == 1) return AdsNode.createLeaf();
        HashMap<I, Pair<Integer, Integer>> splitScore = new HashMap<>();

        Pair<I, Integer> maxInputBase = this.maximalBaseInput(obsTree.getAlphabet(), currentBlock, splitScore);

        HashMap<O, HashSet<Node<I, O>>> oPartitions = this.partitionOnOutput(currentBlock, maxInputBase.first());
        int subTrees = oPartitions.values().stream().map(HashSet::size).reduce(0, Integer::sum);
        int maxInputScore = oPartitions.entrySet().stream()
                .map(e -> this.makeSubtree(obsTree, subTrees, e.getKey(), e.getValue()))
                .reduce(0, Integer::sum);

        HashSet<I> inputsToKeep = splitScore.entrySet().stream()
                .filter(e -> e.getValue().first() + e.getValue().second() >= maxInputScore)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(HashSet::new));

        if (inputsToKeep.isEmpty()) {
            throw new RuntimeException("No input available during ADS comp.");
        }

        I bestInput = null;
        HashMap<O, AdsNode<I, O>> bestChildren = null;
        int bestScore = 0;

        for (I input : inputsToKeep) {
            HashMap<O, HashSet<Node<I, O>>> oParts = this.partitionOnOutput(currentBlock, input);
            int sTrees = oParts.values().stream().map(HashSet::size).reduce(0, Integer::sum);

            int inputScore = 0;
            HashMap<O, AdsNode<I, O>> data = new HashMap<>();

            for (Map.Entry<O, HashSet<Node<I, O>>> entry : oParts.entrySet()) {
                Pair<Integer, Pair<O, AdsNode<I, O>>> res
                        = this.computeOutputSubTree(obsTree, entry.getKey(), entry.getValue(), sTrees);
                inputScore += res.first();
                data.put(res.second().first(), res.second().second());
            }
            if (inputScore < maxInputScore) continue;
            if (bestInput == null || inputScore > bestScore) {
                bestScore = inputScore;
                bestInput = input;
                bestChildren = data;
            }
        }
        if (bestInput == null) {
            throw new RuntimeException("Something is wrong.");
        }

        return new AdsNode<>(bestInput, bestChildren, bestScore);
    }

    private int makeSubtree(ObservationTree<I, O> obsTree, int subTrees, O output, HashSet<Node<I, O>> oPartition) {
        int partitionSize = oPartition.size();
        int childScore = this.constructAds(obsTree, oPartition).getScore();

        return computeRegScore(partitionSize, subTrees, childScore);
    }

    private Pair<Integer, Pair<O, AdsNode<I, O>>> computeOutputSubTree(
            ObservationTree<I, O> obsTree, O output, HashSet<Node<I, O>> oPartition, int subTrees) {
        AdsNode<I, O> outputSubTree = this.constructAds(obsTree, oPartition);

        int outputScore = this.computeRegScore(oPartition.size(), subTrees, outputSubTree.getScore());
        return new Pair<>(outputScore, new Pair<>(output, outputSubTree));
    }

    private Pair<I, Integer> maximalBaseInput(
            HashSet<I> alphabet, HashSet<Node<I, O>> block, HashMap<I, Pair<Integer, Integer>> splitScore) {

        I bestInput = alphabet.iterator().next();
        int bestApartPairs = 0;

        for (I input : alphabet) {
            HashMap<O, HashSet<Node<I, O>>> partition = this.partitionOnOutput(block, input);
            int nonApartPairs = 0;
            int subTrees = 0;
            for (HashSet<Node<I, O>> part : partition.values()) {
                int sz = part.size();
                subTrees += sz;
                nonApartPairs += (sz * (sz-1));
            }
            int apartPairs = subTrees * (subTrees-1) - nonApartPairs;
            splitScore.put(input, new Pair<>(apartPairs, nonApartPairs));
            if (apartPairs > bestApartPairs) {
                bestApartPairs = apartPairs;
                bestInput = input;
            }
        }
        return new Pair<>(bestInput, bestApartPairs);
    }

    private double computeScore(double u, double partitionSize, double subTrees, double childScore) {
        return (partitionSize * (subTrees - partitionSize + childScore)) / u;
    }

    private int computeRegScore(int partitionSize, int subTrees, int childScore) {
        return partitionSize * (subTrees-partitionSize) + childScore;
    }

    private HashMap<O, HashSet<Node<I, O>>> partitionOnOutput(HashSet<Node<I, O>> block, I input) {
        HashMap<O, HashSet<Node<I, O>>> partition = new HashMap<>();
        for (Node<I, O> node : block) {
            Pair<O, Node<I, O>> os = node.getOutputSuccessor(input);
            if (os == null) continue;
            if (!partition.containsKey(os.first())) {
                partition.put(os.first(), new HashSet<>());
            }
            partition.get(os.first()).add(os.second());
        }
        return partition;
    }

    @Override
    public I nextInput(O prevOutput) {
        if (prevOutput != null) {
            AdsNode<I, O> child = this.currentNode.getChildNode(prevOutput);
            if (child == null) return null;
            this.currentNode = child;
        }
        return this.currentNode.getInput();
    }

    @Override
    public double identificationPower() {
        return this.getScore();
    }

    @Override
    public void resetToRoot() {
        this.currentNode = this.initialNode;
    }
}
