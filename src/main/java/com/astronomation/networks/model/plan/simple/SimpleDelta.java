package com.astronomation.networks.model.plan.simple;

import com.astronomation.networks.math.BigRational;
import com.astronomation.networks.model.Identifier;
import com.astronomation.networks.model.plan.TickPlan;

record SimpleDelta(Identifier item, BigRational quantity) implements TickPlan.Delta {
}
