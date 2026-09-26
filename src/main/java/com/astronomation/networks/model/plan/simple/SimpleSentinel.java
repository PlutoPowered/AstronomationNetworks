package com.astronomation.networks.model.plan.simple;

import com.astronomation.networks.model.Network;
import com.astronomation.networks.model.plan.TickPlan;

import java.util.Map;
import java.util.Set;

record SimpleSentinel(Map<Network.Node, Set<TickPlan.Delta>> byNode) implements TickPlan.Sentinel {

    @Override
    public Set<TickPlan.Delta> deltas(Network.Node node) {
        return byNode.getOrDefault(node, Set.of());
    }

}
