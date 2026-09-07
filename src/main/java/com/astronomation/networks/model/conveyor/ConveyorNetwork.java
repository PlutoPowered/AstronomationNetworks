package com.astronomation.networks.model.conveyor;

import com.astronomation.networks.math.BigRational;
import com.astronomation.networks.math.graph.LinearMatrixBuilder;
import com.astronomation.networks.math.graph.LinearMatrixNetworkEdge;
import com.astronomation.networks.math.graph.LinearMatrixNetworkNode;
import com.astronomation.networks.math.simplex.SimplexTableau;
import com.astronomation.networks.model.Identifier;
import com.astronomation.networks.model.Network;
import com.astronomation.networks.model.plan.TickPlan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConveyorNetwork implements Network {

    public static final class ConveyorNode implements Network.Node {

        private final Identifier name;
        private final LinearMatrixNetworkNode.Type type;
        private List<ConveyorEdge> inputs;
        private List<ConveyorEdge> outputs;

        public ConveyorNode(Identifier name, LinearMatrixNetworkNode.Type type) {
            this.name = name;
            this.type = type;
        }

        public List<ConveyorEdge> inputs() {
            return this.inputs;
        }

        public ConveyorNode inputs(List<ConveyorEdge> inputs) {
            this.inputs = inputs;
            return this;
        }

        public List<ConveyorEdge> outputs() {
            return this.outputs;
        }

        public ConveyorNode outputs(List<ConveyorEdge> outputs) {
            this.outputs = outputs;
            return this;
        }

        public Identifier name() {
            return this.name;
        }

        public LinearMatrixNetworkNode.Type type() {
            return this.type;
        }

    }

    public static final class ConveyorEdge {

        private final ConveyorNode link;
        private final Identifier item;
        private final BigRational throughputMax;
        private final BigRational quantity;
        private final long lengthTicks;

        public ConveyorEdge(ConveyorNode link, Identifier item, BigRational throughputMax, BigRational quantity, long lengthTicks) {
            this.link = link;
            this.item = item;
            this.throughputMax = throughputMax;
            this.quantity = quantity;
            this.lengthTicks = lengthTicks;
        }

        public ConveyorNode link() {
            return this.link;
        }

        public Identifier item() {
            return this.item;
        }

        public BigRational throughputMax() {
            return this.throughputMax;
        }

        public BigRational quantity() {
            return this.quantity;
        }

        public long lengthTicks() {
            return this.lengthTicks;
        }

    }

    private final List<ConveyorNode> nodes;

    public ConveyorNetwork(Collection<? extends ConveyorNode> nodes) {
        if (nodes == null) {
            throw new IllegalArgumentException("nodes must not be null");
        }
        this.nodes = List.copyOf(nodes);
    }

    @Override
    public TickPlan plan(TickPlan.Builder builder) {
        Map<ConveyorNode, LinearMatrixNetworkNode> matrixNodes = new IdentityHashMap<>();
        for (ConveyorNode node : nodes) {
            matrixNodes.put(node, new LinearMatrixNetworkNode(node.name(), node.type()));
        }
        for (ConveyorNode node : nodes) {
            matrixNodes.get(node).inputs(toMatrixEdges(orEmpty(node.inputs()), matrixNodes));
            matrixNodes.get(node).outputs(toMatrixEdges(orEmpty(node.outputs()), matrixNodes));
        }

        SimplexTableau tableau = new LinearMatrixBuilder()
                .allNodes(new HashSet<>(matrixNodes.values()))
                .build()
                .solve();

        if (!tableau.solved()) {
            throw new IllegalStateException("ConveyorNetwork has no feasible steady-state solution");
        }

        List<ConveyorNode> order = topologicalOrder(tableau);

        Map<ConveyorNode, Long> readyTick = new IdentityHashMap<>();
        for (ConveyorNode node : order) {
            long ready = 0;
            for (ConveyorEdge edge : orEmpty(node.inputs())) {
                if (flowOf(tableau, edge.link(), node).signum() > 0) {
                    ready = Math.max(ready, readyTick.get(edge.link()) + edge.lengthTicks());
                }
            }
            readyTick.put(node, ready);
        }

        Map<ConveyorNode, Map<Identifier, BigRational>> produced = new IdentityHashMap<>();
        Map<ConveyorNode, Map<Identifier, BigRational>> consumed = new IdentityHashMap<>();
        for (ConveyorNode node : nodes) {
            produced.put(node, producedByItem(tableau, node));
            consumed.put(node, consumedByItem(tableau, node));
        }

        long terminalTick = 0;
        for (long tick : readyTick.values()) {
            terminalTick = Math.max(terminalTick, tick);
        }

        for (long tick = 0; tick < terminalTick; tick++) {
            builder = appendTick(builder.preCycleTick(), readyTick, produced, consumed, tick);
        }

        builder = appendTick(builder.terminalCycleTick(), readyTick, produced, consumed, terminalTick);
        builder = appendTick(builder.terminalAverage(), readyTick, produced, consumed, terminalTick);

        return builder.build();
    }

    private List<ConveyorNode> topologicalOrder(SimplexTableau tableau) {
        Map<ConveyorNode, List<ConveyorNode>> adjacency = new IdentityHashMap<>();
        Map<ConveyorNode, Integer> inDegree = new IdentityHashMap<>();
        for (ConveyorNode node : nodes) {
            adjacency.put(node, new ArrayList<>());
            inDegree.put(node, 0);
        }

        for (ConveyorNode node : nodes) {
            for (ConveyorEdge edge : orEmpty(node.outputs())) {
                if (flowOf(tableau, node, edge.link()).signum() > 0) {
                    adjacency.get(node).add(edge.link());
                    inDegree.merge(edge.link(), 1, Integer::sum);
                }
            }
        }

        Deque<ConveyorNode> queue = new ArrayDeque<>();
        for (ConveyorNode node : nodes) {
            if (inDegree.get(node) == 0) {
                queue.add(node);
            }
        }

        List<ConveyorNode> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            ConveyorNode node = queue.poll();
            order.add(node);
            for (ConveyorNode dest : adjacency.get(node)) {
                if (inDegree.merge(dest, -1, Integer::sum) == 0) {
                    queue.add(dest);
                }
            }
        }

        if (order.size() != nodes.size()) {
            throw new IllegalStateException("ConveyorNetwork has a positive-flow recycle loop that cannot bootstrap from empty belts");
        }

        return order;
    }

    private Map<Identifier, BigRational> producedByItem(SimplexTableau tableau, ConveyorNode node) {
        Map<Identifier, BigRational> sums = new LinkedHashMap<>();
        for (ConveyorEdge edge : orEmpty(node.outputs())) {
            sums.merge(edge.item(), flowOf(tableau, node, edge.link()), BigRational::add);
        }
        return sums;
    }

    private Map<Identifier, BigRational> consumedByItem(SimplexTableau tableau, ConveyorNode node) {
        Map<Identifier, BigRational> sums = new LinkedHashMap<>();
        for (ConveyorEdge edge : orEmpty(node.inputs())) {
            sums.merge(edge.item(), flowOf(tableau, edge.link(), node), BigRational::add);
        }
        return sums;
    }

    private TickPlan.Builder appendTick(TickPlan.Sentinel.Builder sentinel, Map<ConveyorNode, Long> readyTick, Map<ConveyorNode, Map<Identifier, BigRational>> produced, Map<ConveyorNode, Map<Identifier, BigRational>> consumed, long tick) {
        for (ConveyorNode node : nodes) {
            boolean ready = tick >= readyTick.get(node);
            for (Map.Entry<Identifier, BigRational> entry : produced.get(node).entrySet()) {
                sentinel.delta(node, entry.getKey(), ready ? entry.getValue() : BigRational.ZERO);
            }
            for (Map.Entry<Identifier, BigRational> entry : consumed.get(node).entrySet()) {
                sentinel.delta(node, entry.getKey(), ready ? entry.getValue() : BigRational.ZERO);
            }
        }
        return sentinel.build();
    }

    private static BigRational flowOf(SimplexTableau tableau, ConveyorNode source, ConveyorNode destination) {
        String var = "e_" + source.name().id() + "_" + destination.name().id();
        return tableau.hasValue(var) ? tableau.value(var) : BigRational.ZERO;
    }

    private static List<ConveyorEdge> orEmpty(List<ConveyorEdge> edges) {
        return edges != null ? edges : List.of();
    }

    private static List<LinearMatrixNetworkEdge> toMatrixEdges(List<ConveyorEdge> edges, Map<ConveyorNode, LinearMatrixNetworkNode> matrixNodes) {
        List<LinearMatrixNetworkEdge> matrixEdges = new ArrayList<>();
        for (ConveyorEdge edge : edges) {
            matrixEdges.add(new LinearMatrixNetworkEdge(matrixNodes.get(edge.link()), edge.item(), edge.throughputMax(), edge.quantity()));
        }
        return matrixEdges;
    }

}
