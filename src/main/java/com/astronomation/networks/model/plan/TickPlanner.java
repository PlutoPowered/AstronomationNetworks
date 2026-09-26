package com.astronomation.networks.model.plan;

public interface TickPlanner {

    TickPlan plan();

    /**
     * @return true if there is another iteration step, false if finished
     */
    boolean iterate();

}
