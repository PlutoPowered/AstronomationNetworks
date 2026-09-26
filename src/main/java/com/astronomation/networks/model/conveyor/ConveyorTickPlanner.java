package com.astronomation.networks.model.conveyor;

import com.astronomation.networks.math.BigRational;
import com.astronomation.networks.math.graph.LinearMatrixBuilder;
import com.astronomation.networks.math.graph.LinearMatrixNetworkEdge;
import com.astronomation.networks.math.graph.LinearMatrixNetworkNode;
import com.astronomation.networks.math.simplex.SimplexMatrix;
import com.astronomation.networks.math.simplex.SimplexTableau;
import com.astronomation.networks.model.Identifier;
import com.astronomation.networks.model.conveyor.ConveyorNetwork.ConveyorEdge;
import com.astronomation.networks.model.conveyor.ConveyorNetwork.ConveyorNode;
import com.astronomation.networks.model.plan.TickPlan;
import com.astronomation.networks.model.plan.TickPlanner;
import com.astronomation.networks.model.plan.exception.IllegalPlanStateException;
import com.astronomation.networks.model.plan.exception.InvalidCycleException;
import com.astronomation.networks.model.plan.exception.UnsolvableNetworkException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pausable planner for a {@link ConveyorNetwork}. Each call to {@link #iterate()} performs one unit of work:
 * building the LP, one simplex pivot, ordering, readiness computation, one pre-cycle tick, or finalization.
 */
public class ConveyorTickPlanner implements TickPlanner {

    private enum Phase {
        BUILD,
        SOLVE,
        ORDER,
        READY,
        PRECYCLE,
        FINISH,
        DONE
    }

    private final List<ConveyorNode> nodes;
    private TickPlan.Builder builder;
    private Phase phase = Phase.BUILD;

    private SimplexTableau tableau;
    private List<ConveyorNode> order;
    private Map<ConveyorNode, Long> readyTick;
    private Map<ConveyorNode, Map<Identifier, BigRational>> produced;
    private Map<ConveyorNode, Map<Identifier, BigRational>> consumed;
    private long terminalTick;
    private long tick;
    private TickPlan result;

    public ConveyorTickPlanner(List<ConveyorNode> nodes, TickPlan.Builder builder) {
        this.nodes = nodes;
        this.builder = builder;
    }

    @Override
    public TickPlan plan() {
        if (phase != Phase.DONE) {
            throw new IllegalPlanStateException("plan() called before iterate() finished");
        }
        return result;
    }

    @Override
    public boolean iterate() {
        switch (phase) {
            case BUILD -> build();
            case SOLVE -> solve();
            case ORDER -> order();
            case READY -> ready();
            case PRECYCLE -> preCycle();
            case FINISH -> finish();
            case DONE -> {
                return false;
            }
        }
        return phase != Phase.DONE;
    }

    private void build() {
        Map<ConveyorNode, LinearMatrixNetworkNode> matrixNodes = new IdentityHashMap<>();
        for (ConveyorNode node : nodes) {
            matrixNodes.put(node, new LinearMatrixNetworkNode(node.name(), node.type()));
        }
        for (ConveyorNode node : nodes) {
            matrixNodes.get(node).inputs(toMatrixEdges(orEmpty(node.inputs()), matrixNodes));
            matrixNodes.get(node).outputs(toMatrixEdges(orEmpty(node.outputs()), matrixNodes));
        }

        tableau = new LinearMatrixBuilder().allNodes(new HashSet<>(matrixNodes.values())).build();
        phase = Phase.SOLVE;
    }

    private void solve() {
        if (tableau.matrix().step() == SimplexMatrix.StepResult.CONTINUE) {
            return;
        }
        if (!tableau.solved()) {
            throw new UnsolvableNetworkException("ConveyorNetwork has no feasible steady-state solution");
        }
        phase = Phase.ORDER;
    }

    private void order() {
        order = topologicalOrder(tableau);
        phase = Phase.READY;
    }

    private void ready() {
        readyTick = new IdentityHashMap<>();
        for (ConveyorNode node : order) {
            long ready = 0;
            for (ConveyorEdge edge : orEmpty(node.inputs())) {
                if (flowOf(tableau, edge.link(), node).signum() > 0) {
                    ready = Math.max(ready, readyTick.get(edge.link()) + edge.lengthTicks());
                }
            }
            readyTick.put(node, ready);
        }

        produced = new IdentityHashMap<>();
        consumed = new IdentityHashMap<>();
        for (ConveyorNode node : nodes) {
            produced.put(node, producedByItem(tableau, node));
            consumed.put(node, consumedByItem(tableau, node));
        }

        terminalTick = 0;
        for (long ready : readyTick.values()) {
            terminalTick = Math.max(terminalTick, ready);
        }

        tick = 0;
        phase = tick < terminalTick ? Phase.PRECYCLE : Phase.FINISH;
    }

    private void preCycle() {
        builder = appendTick(builder.preCycleTick(), tick);
        tick++;
        if (tick >= terminalTick) {
            phase = Phase.FINISH;
        }
    }

    private void finish() {
        builder = appendTick(builder.terminalCycleTick(), terminalTick);
        builder = appendTick(builder.terminalAverage(), terminalTick);
        result = builder.build();
        phase = Phase.DONE;
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
            List<ConveyorNode> unresolved = new ArrayList<>(nodes);
            unresolved.removeAll(order);
            throw new InvalidCycleException(findCycle(unresolved, adjacency));
        }

        return order;
    }

    private List<ConveyorNode> findCycle(List<ConveyorNode> unresolved, Map<ConveyorNode, List<ConveyorNode>> adjacency) {
        Set<ConveyorNode> remaining = Collections.newSetFromMap(new IdentityHashMap<>());
        remaining.addAll(unresolved);

        List<ConveyorNode> path = new ArrayList<>();
        Set<ConveyorNode> onPath = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<ConveyorNode> visited = Collections.newSetFromMap(new IdentityHashMap<>());

        for (ConveyorNode start : unresolved) {
            if (!visited.contains(start)) {
                List<ConveyorNode> cycle = findCycle(start, remaining, adjacency, path, onPath, visited);
                if (cycle != null) {
                    return cycle;
                }
            }
        }

        throw new IllegalPlanStateException("expected a cycle among unresolved nodes");
    }

    private List<ConveyorNode> findCycle(ConveyorNode node, Set<ConveyorNode> remaining, Map<ConveyorNode, List<ConveyorNode>> adjacency, List<ConveyorNode> path, Set<ConveyorNode> onPath, Set<ConveyorNode> visited) {
        visited.add(node);
        path.add(node);
        onPath.add(node);

        for (ConveyorNode next : adjacency.get(node)) {
            if (!remaining.contains(next)) {
                continue;
            }
            if (onPath.contains(next)) {
                return new ArrayList<>(path.subList(path.indexOf(next), path.size()));
            }
            if (!visited.contains(next)) {
                List<ConveyorNode> cycle = findCycle(next, remaining, adjacency, path, onPath, visited);
                if (cycle != null) {
                    return cycle;
                }
            }
        }

        path.remove(path.size() - 1);
        onPath.remove(node);
        return null;
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

    private TickPlan.Builder appendTick(TickPlan.Sentinel.Builder sentinel, long tick) {
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
