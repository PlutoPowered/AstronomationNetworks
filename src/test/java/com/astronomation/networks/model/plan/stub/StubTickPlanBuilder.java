package com.astronomation.networks.model.plan.stub;

import com.astronomation.networks.model.plan.TickPlan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StubTickPlanBuilder implements TickPlan.Builder {

    private final List<TickPlan.Sentinel> preCycle = new ArrayList<>();
    private final List<TickPlan.Sentinel> terminalCycle = new ArrayList<>();
    private TickPlan.Sentinel terminalAverage = new StubSentinel(Map.of());

    @Override
    public TickPlan.Sentinel.Builder preCycleTick() {
        return new StubSentinelBuilder(this, preCycle::add);
    }

    @Override
    public TickPlan.Sentinel.Builder terminalCycleTick() {
        return new StubSentinelBuilder(this, terminalCycle::add);
    }

    @Override
    public TickPlan.Sentinel.Builder terminalAverage() {
        return new StubSentinelBuilder(this, sentinel -> this.terminalAverage = sentinel);
    }

    @Override
    public TickPlan build() {
        return new StubTickPlan(List.copyOf(preCycle), List.copyOf(terminalCycle), terminalAverage);
    }

}
