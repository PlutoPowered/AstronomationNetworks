package com.astronomation.networks.model;

import com.astronomation.networks.model.plan.TickPlan;

public interface Network {

    TickPlan plan(TickPlan.Builder builder);

    interface Node {

    }

}
