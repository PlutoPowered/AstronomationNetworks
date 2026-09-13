package com.astronomation.networks.model.plan.stub;

import com.astronomation.networks.math.BigRational;
import com.astronomation.networks.model.Identifier;
import com.astronomation.networks.model.Network;
import com.astronomation.networks.model.plan.TickPlan;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

class StubSentinelBuilder implements TickPlan.Sentinel.Builder {

    private final TickPlan.Builder parent;
    private final Consumer<TickPlan.Sentinel> commit;
    private final Map<Network.Node, Set<TickPlan.Delta>> byNode = new IdentityHashMap<>();

    StubSentinelBuilder(TickPlan.Builder parent, Consumer<TickPlan.Sentinel> commit) {
        this.parent = parent;
        this.commit = commit;
    }

    @Override
    public TickPlan.Sentinel.Builder delta(Network.Node node, Identifier item, BigRational quantity) {
        byNode.computeIfAbsent(node, n -> new LinkedHashSet<>()).add(new StubDelta(item, quantity));
        return this;
    }

    @Override
    public TickPlan.Builder build() {
        commit.accept(new StubSentinel(Collections.unmodifiableMap(new IdentityHashMap<>(byNode))));
        return parent;
    }

}
