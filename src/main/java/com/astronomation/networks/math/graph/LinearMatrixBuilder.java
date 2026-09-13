package com.astronomation.networks.math.graph;

import com.astronomation.networks.math.BigRational;
import com.astronomation.networks.math.simplex.SimplexBuilder;
import com.astronomation.networks.math.simplex.SimplexTableau;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LinearMatrixBuilder {
    private Set<LinearMatrixNetworkNode> allNodes = new HashSet<>();

    public LinearMatrixBuilder allNodes(Set<LinearMatrixNetworkNode> allNodes) {
        this.allNodes = allNodes;
        return this;
    }

    public LinearMatrixBuilder addNodes(Set<LinearMatrixNetworkNode> nodes) {
        this.allNodes.addAll(nodes);
        return this;
    }

    public LinearMatrixBuilder discoverNodes(LinearMatrixNetworkNode root) {
        this.findAllConnectedNodes(root, this.allNodes);
        return this;
    }

    private void findAllConnectedNodes(LinearMatrixNetworkNode start, Set<LinearMatrixNetworkNode> visited) {
        Deque<LinearMatrixNetworkNode> stack = new ArrayDeque<>();
        stack.push(start);
        visited.add(start);

        while (!stack.isEmpty()) {
            LinearMatrixNetworkNode current = stack.pop();

            for (LinearMatrixNetworkEdge edge : current.inputs()) {
                LinearMatrixNetworkNode node = edge.link();
                if (!visited.contains(node)) {
                    visited.add(node);
                    stack.push(node);
                }
            }

            for (LinearMatrixNetworkEdge edge : current.outputs()) {
                LinearMatrixNetworkNode node = edge.link();
                if (!visited.contains(node)) {
                    visited.add(node);
                    stack.push(node);
                }
            }
        }
    }

    private Map<List<LinearMatrixNetworkNode>, String> vars = new HashMap<>();

    public SimplexTableau build() {
        // --- Phase 1: Populate variables (one per directed edge) ---
        Map<List<LinearMatrixNetworkNode>, LinearMatrixNetworkEdge> edgeByKey = new HashMap<>();

        for (LinearMatrixNetworkNode node : allNodes) {
            List<LinearMatrixNetworkEdge> outs = node.outputs();
            if (outs == null) continue;
            for (LinearMatrixNetworkEdge edge : outs) {
                LinearMatrixNetworkNode dest = edge.link();
                List<LinearMatrixNetworkNode> key = List.of(node, dest);
                if (!vars.containsKey(key)) {
                    vars.put(key, "e_" + node.name().id() + "_" + dest.name().id());
                    edgeByKey.put(key, edge);
                }
            }
        }

        if (vars.isEmpty()) {
            throw new IllegalStateException("Graph has no edges; cannot form LP");
        }

        SimplexBuilder builder = new SimplexBuilder();

        // --- Phase 2: Objective — maximize sum of PRODUCER output flows ---
        SimplexBuilder.RowBuilder goal = builder.row();
        boolean goalHasVars = false;

        for (LinearMatrixNetworkNode node : allNodes) {
            if (node.type() != LinearMatrixNetworkNode.Type.PRODUCER) continue;
            List<LinearMatrixNetworkEdge> outs = node.outputs();
            if (outs == null) continue;
            for (LinearMatrixNetworkEdge edge : outs) {
                String v = vars.get(List.of(node, edge.link()));
                if (v != null) {
                    goal.var(1L, v);
                    goalHasVars = true;
                }
            }
        }

        if (!goalHasVars) {
            throw new IllegalStateException("No PRODUCER node outputs found; cannot form objective function");
        }
        goal.maximize();

        // --- Phase 3: Conservation and ratio constraints per node type ---
        for (LinearMatrixNetworkNode node : allNodes) {
            List<LinearMatrixNetworkEdge> ins  = node.inputs()  != null ? node.inputs()  : List.of();
            List<LinearMatrixNetworkEdge> outs = node.outputs() != null ? node.outputs() : List.of();

            switch (node.type()) {
                case MACHINE -> {
                    // Enforce recipe ratios via a machine-rate variable: flow_edge = quantity * r_machine
                    // Each edge gets its own equality: flow_edge - quantity * r_machine = 0
                    String rateVar = "r_" + node.name().id();
                    boolean anyRatio = false;

                    for (LinearMatrixNetworkEdge edge : ins) {
                        BigRational qty = edge.quantity();
                        if (qty != null && qty.compareTo(BigRational.ZERO) != 0) {
                            String fv = vars.get(List.of(edge.link(), node));
                            if (fv != null) {
                                builder.row().var(1L, fv).var(qty.negate(), rateVar).equalTo(0);
                                anyRatio = true;
                            }
                        }
                    }
                    for (LinearMatrixNetworkEdge edge : outs) {
                        BigRational qty = edge.quantity();
                        if (qty != null && qty.compareTo(BigRational.ZERO) != 0) {
                            String fv = vars.get(List.of(node, edge.link()));
                            if (fv != null) {
                                builder.row().var(1L, fv).var(qty.negate(), rateVar).equalTo(0);
                                anyRatio = true;
                            }
                        }
                    }

                    if (!anyRatio) {
                        // No quantity data — fall back to simple conservation
                        SimplexBuilder.RowBuilder row = builder.row();
                        boolean hasTerms = false;
                        for (LinearMatrixNetworkEdge e : ins) {
                            String fv = vars.get(List.of(e.link(), node));
                            if (fv != null) { row.var(1L, fv); hasTerms = true; }
                        }
                        for (LinearMatrixNetworkEdge e : outs) {
                            String fv = vars.get(List.of(node, e.link()));
                            if (fv != null) { row.var(-1L, fv); hasTerms = true; }
                        }
                        if (hasTerms) row.equalTo(0);
                    }
                }

                case SPLITTER, MERGER -> {
                    // Simple conservation: sum(inputs) = sum(outputs)
                    SimplexBuilder.RowBuilder row = builder.row();
                    boolean hasTerms = false;
                    for (LinearMatrixNetworkEdge edge : ins) {
                        String fv = vars.get(List.of(edge.link(), node));
                        if (fv != null) { row.var(1L, fv); hasTerms = true; }
                    }
                    for (LinearMatrixNetworkEdge edge : outs) {
                        String fv = vars.get(List.of(node, edge.link()));
                        if (fv != null) { row.var(-1L, fv); hasTerms = true; }
                    }
                    if (hasTerms) row.equalTo(0);
                }

                case PRODUCER, SINK -> {
                    // No conservation row; only capacity constraints (Phase 4)
                }
            }
        }

        // --- Phase 4: Capacity constraints on all edges ---
        for (Map.Entry<List<LinearMatrixNetworkNode>, LinearMatrixNetworkEdge> entry : edgeByKey.entrySet()) {
            BigRational cap = entry.getValue().throughputMax();
            if (cap != null && cap.compareTo(BigRational.ZERO) > 0) {
                String v = vars.get(entry.getKey());
                builder.row().var(1L, v).lessThanOrEqualTo(cap);
            }
        }

        return builder.build();
    }

}
