package com.astronomation.networks.model.plan.simple;

import com.astronomation.networks.model.plan.TickPlan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SimpleTickPlanBuilder implements TickPlan.Builder {

    private final List<TickPlan.Sentinel> preCycle = new ArrayList<>();
    private final List<TickPlan.Sentinel> terminalCycle = new ArrayList<>();
    private TickPlan.Sentinel terminalAverage = new SimpleSentinel(Map.of());

    @Override
    public TickPlan.Sentinel.Builder preCycleTick() {
        return new SimpleSentinelBuilder(this, preCycle::add);
    }

    @Override
    public TickPlan.Sentinel.Builder terminalCycleTick() {
        return new SimpleSentinelBuilder(this, terminalCycle::add);
    }

    @Override
    public TickPlan.Sentinel.Builder terminalAverage() {
        return new SimpleSentinelBuilder(this, sentinel -> this.terminalAverage = sentinel);
    }

    @Override
    public TickPlan build() {
        return new SimpleTickPlan(List.copyOf(preCycle), List.copyOf(terminalCycle), terminalAverage);
    }

}
