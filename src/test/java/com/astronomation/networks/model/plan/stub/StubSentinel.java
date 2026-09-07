package com.astronomation.networks.model.plan.stub;

import com.astronomation.networks.model.Network;
import com.astronomation.networks.model.plan.TickPlan;

import java.util.Map;
import java.util.Set;

record StubSentinel(Map<Network.Node, Set<TickPlan.Delta>> byNode) implements TickPlan.Sentinel {

    @Override
    public Set<TickPlan.Delta> deltas(Network.Node node) {
        return byNode.getOrDefault(node, Set.of());
    }

}
