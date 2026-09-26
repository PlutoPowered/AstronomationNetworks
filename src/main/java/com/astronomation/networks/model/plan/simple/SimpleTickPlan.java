package com.astronomation.networks.model.plan.simple;

import com.astronomation.networks.model.plan.TickPlan;

import java.util.List;

record SimpleTickPlan(List<TickPlan.Sentinel> preCycle, List<TickPlan.Sentinel> terminalCycle, TickPlan.Sentinel terminalAverage) implements TickPlan {
}
