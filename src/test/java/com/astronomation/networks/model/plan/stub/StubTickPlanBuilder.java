package com.astronomation.networks.model.plan.stub;

import com.astronomation.networks.math.BigRational;
import com.astronomation.networks.model.Network;
import com.astronomation.networks.model.plan.TickPlan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StubTickPlanBuilder implements TickPlan.Builder {

    private final List<TickPlan.Sentinel> preCycle = new ArrayList<>();
    private final List<TickPlan.Sentinel> terminalCycle = new ArrayList<>();
    private TickPlan.Sentinel terminalAverage = new StubSentinel(Map.of());

    @Override
    public TickPlan.Sentinel.Builder sentinel() {
        return new StubSentinelBuilder();
    }

    @Override
    public TickPlan.Builder preCycleTick(TickPlan.Sentinel sentinel) {
        preCycle.add(sentinel);
        return this;
    }

    @Override
    public TickPlan.Builder terminalCycleTick(TickPlan.Sentinel sentinel) {
        terminalCycle.add(sentinel);
        return this;
    }

    @Override
    public TickPlan.Builder terminalAverage(TickPlan.Sentinel sentinel) {
        this.terminalAverage = sentinel;
        return this;
    }

    @Override
    public TickPlan build() {
        return new StubTickPlan(List.copyOf(preCycle), List.copyOf(terminalCycle), terminalAverage);
    }

}

record StubTickPlan(List<TickPlan.Sentinel> preCycle, List<TickPlan.Sentinel> terminalCycle,
                     TickPlan.Sentinel terminalAverage) implements TickPlan {
}

record StubDelta(String item, BigRational quantity) implements TickPlan.Delta {
}

record StubSentinel(Map<Network.Node, Set<TickPlan.Delta>> byNode) implements TickPlan.Sentinel {
    @Override
    public Set<TickPlan.Delta> deltas(Network.Node node) {
        return byNode.getOrDefault(node, Set.of());
    }
}

class StubSentinelBuilder implements TickPlan.Sentinel.Builder {

    private final Map<Network.Node, Set<TickPlan.Delta>> byNode = new IdentityHashMap<>();

    @Override
    public TickPlan.Sentinel.Builder delta(Network.Node node, String item, BigRational quantity) {
        byNode.computeIfAbsent(node, n -> new LinkedHashSet<>()).add(new StubDelta(item, quantity));
        return this;
    }

    @Override
    public TickPlan.Sentinel build() {
        return new StubSentinel(Collections.unmodifiableMap(new IdentityHashMap<>(byNode)));
    }

}
