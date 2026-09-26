package com.astronomation.networks.model;

import com.astronomation.networks.model.plan.TickPlan;
import com.astronomation.networks.model.plan.TickPlanner;

public interface Network {

    TickPlanner planner(TickPlan.Builder builder);

    default TickPlan plan(TickPlan.Builder builder) {
        TickPlanner planner = planner(builder);
        while (planner.iterate());
        return planner.plan();
    }

    interface Node {

    }

}
