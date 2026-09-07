package com.astronomation.networks.model.plan.stub;

import com.astronomation.networks.math.BigRational;
import com.astronomation.networks.model.plan.TickPlan;

record StubDelta(String item, BigRational quantity) implements TickPlan.Delta {
}
