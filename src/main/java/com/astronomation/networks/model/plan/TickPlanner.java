package com.astronomation.networks.model.plan;

public interface TickPlanner {

    TickPlan plan();

    boolean iterate();

}
