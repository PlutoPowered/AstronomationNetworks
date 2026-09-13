package com.astronomation.networks.model.plan.stub;

import com.astronomation.networks.model.plan.TickPlan;

import java.util.List;

record StubTickPlan(List<TickPlan.Sentinel> preCycle, List<TickPlan.Sentinel> terminalCycle, TickPlan.Sentinel terminalAverage) implements TickPlan {
}
