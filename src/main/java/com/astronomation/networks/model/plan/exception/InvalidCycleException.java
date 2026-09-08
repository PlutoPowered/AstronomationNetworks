package com.astronomation.networks.model.plan.exception;

import com.astronomation.networks.model.Network;

import java.util.List;

public class InvalidCycleException extends UnsolvableNetworkException {

    private final List<Network.Node> cycle;

    public InvalidCycleException(List<? extends Network.Node> cycle) {
        super("TickPlan cannot be built: network contains a cycle that cannot bootstrap from an empty starting state");
        this.cycle = List.copyOf(cycle);
    }

    public List<Network.Node> cycle() {
        return this.cycle;
    }

}
