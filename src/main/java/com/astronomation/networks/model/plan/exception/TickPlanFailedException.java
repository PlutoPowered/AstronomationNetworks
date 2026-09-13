package com.astronomation.networks.model.plan.exception;

public abstract class TickPlanFailedException extends RuntimeException {

    protected TickPlanFailedException(String message) {
        super(message);
    }

}
