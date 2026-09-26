package com.astronomation.networks.model.instant;

import com.astronomation.networks.math.BigRational;
import com.astronomation.networks.model.Identifier;
import com.astronomation.networks.model.instant.InstantaneousUniversalNetwork.Consumer;
import com.astronomation.networks.model.instant.InstantaneousUniversalNetwork.DeficitPolicy;
import com.astronomation.networks.model.instant.InstantaneousUniversalNetwork.Producer;
import com.astronomation.networks.model.instant.InstantaneousUniversalNetwork.Storage;
import com.astronomation.networks.model.plan.TickPlan;
import com.astronomation.networks.model.plan.TickPlanner;
import com.astronomation.networks.model.plan.exception.IllegalPlanStateException;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pausable planner for an {@link InstantaneousUniversalNetwork}. Each call to {@link #iterate()} performs one unit
 * of work: setup, computing one storage-phase segment, emitting one whole pre-cycle tick, emitting the fractional
 * transition tick, or finalization.
 */
public class InstantaneousTickPlanner implements TickPlanner {

    private enum Phase {
        SETUP,
        SEGMENT,
        WHOLE,
        TRANSITION,
        FINISH,
        DONE
    }

    private final List<Producer> producers;
    private final List<Consumer> consumers;
    private final List<Storage> storages;
    private final DeficitPolicy deficitPolicy;
    private final Identifier item;
    private final BigRational totalProduction;
    private final BigRational totalConsumption;
    private TickPlan.Builder builder;
    private Phase phase = Phase.SETUP;

    private int cmp;
    private BigRational terminalDelivered;
    private boolean charging;
    private BigRational driving;
    private Map<Storage, BigRational> remaining;
    private Set<Storage> active;

    private Map<Storage, BigRational> rate;
    private BigRational delivered;
    private long wholeTicks;
    private long wholeTicksEmitted;
    private BigRational frac;

    private TickPlan result;

    public InstantaneousTickPlanner(List<Producer> producers, List<Consumer> consumers, List<Storage> storages, DeficitPolicy deficitPolicy, Identifier item, BigRational totalProduction, BigRational totalConsumption, TickPlan.Builder builder) {
        this.producers = producers;
        this.consumers = consumers;
        this.storages = storages;
        this.deficitPolicy = deficitPolicy;
        this.item = item;
        this.totalProduction = totalProduction;
        this.totalConsumption = totalConsumption;
        this.builder = builder;
    }

    @Override
    public TickPlan plan() {
        if (phase != Phase.DONE) {
            throw new IllegalPlanStateException("plan() called before iterate() finished");
        }
        return result;
    }

    @Override
    public boolean iterate() {
        switch (phase) {
            case SETUP -> setup();
            case SEGMENT -> segment();
            case WHOLE -> whole();
            case TRANSITION -> transition();
            case FINISH -> finish();
            case DONE -> {
                return false;
            }
        }
        return phase != Phase.DONE;
    }

    private void setup() {
        cmp = totalProduction.compareTo(totalConsumption);
        if (cmp == 0) {
            terminalDelivered = totalConsumption;
            phase = Phase.FINISH;
            return;
        }

        charging = cmp > 0;
        driving = charging ? totalProduction.sub(totalConsumption) : totalConsumption.sub(totalProduction);
        terminalDelivered = charging ? totalConsumption : totalProduction;

        remaining = new IdentityHashMap<>();
        active = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Storage s : storages) {
            BigRational cap = charging ? s.chargeRatePerTick() : s.dischargeRatePerTick();
            BigRational boundaryRemaining = charging ? s.maxStorage().sub(s.currentStorage()) : s.currentStorage();
            if (cap.signum() > 0 && boundaryRemaining.signum() > 0) {
                remaining.put(s, boundaryRemaining);
                active.add(s);
            }
        }

        phase = nextSegmentPhase();
    }

    private Phase nextSegmentPhase() {
        return !active.isEmpty() && driving.signum() > 0 ? Phase.SEGMENT : Phase.FINISH;
    }

    private void segment() {
        BigRational totalCap = BigRational.ZERO;
        for (Storage s : active) {
            totalCap = totalCap.add(charging ? s.chargeRatePerTick() : s.dischargeRatePerTick());
        }

        BigRational totalToDistribute = BigRational.min(driving, totalCap);

        rate = new IdentityHashMap<>();
        for (Storage s : active) {
            BigRational cap = charging ? s.chargeRatePerTick() : s.dischargeRatePerTick();
            rate.put(s, cap.mul(totalToDistribute).div(totalCap));
        }

        delivered = charging ? totalConsumption : totalProduction.add(totalToDistribute);

        BigRational tMin = null;
        for (Storage s : active) {
            tMin = BigRational.min(tMin, remaining.get(s).div(rate.get(s)));
        }

        wholeTicks = tMin.numerator().divide(tMin.denominator()).longValueExact();
        frac = tMin.sub(BigRational.of(wholeTicks));
        wholeTicksEmitted = 0;

        phase = wholeTicks > 0 ? Phase.WHOLE : Phase.TRANSITION;
    }

    private void whole() {
        builder = appendTick(builder.preCycleTick(), active, rate, charging, delivered);
        wholeTicksEmitted++;
        if (wholeTicksEmitted >= wholeTicks) {
            for (Storage s : active) {
                remaining.put(s, remaining.get(s).sub(rate.get(s).mul(BigRational.of(wholeTicks))));
            }
            phase = Phase.TRANSITION;
        }
    }

    private void transition() {
        if (frac.signum() > 0) {
            Map<Storage, BigRational> transitionRate = new IdentityHashMap<>();
            for (Storage s : active) {
                transitionRate.put(s, BigRational.min(rate.get(s), remaining.get(s)));
            }
            builder = appendTick(builder.preCycleTick(), active, transitionRate, charging, delivered);
            for (Storage s : active) {
                remaining.put(s, remaining.get(s).sub(transitionRate.get(s)));
            }
        }

        active.removeIf(s -> remaining.get(s).compareTo(BigRational.ZERO) == 0);
        phase = nextSegmentPhase();
    }

    private void finish() {
        Set<Storage> noActiveStorage = Set.of();
        Map<Storage, BigRational> noRates = Map.of();
        builder = appendTick(builder.terminalCycleTick(), noActiveStorage, noRates, cmp >= 0, terminalDelivered);
        builder = appendTick(builder.terminalAverage(), noActiveStorage, noRates, cmp >= 0, terminalDelivered);
        result = builder.build();
        phase = Phase.DONE;
    }

    private TickPlan.Builder appendTick(TickPlan.Sentinel.Builder sentinel, Set<Storage> active, Map<Storage, BigRational> rate, boolean charging, BigRational delivered) {
        for (Producer p : producers) {
            sentinel.delta(p, item, p.productionPerTick());
        }

        for (Consumer c : consumers) {
            sentinel.delta(c, item, deliveredRateFor(c, delivered));
        }

        for (Storage s : storages) {
            BigRational magnitude = active.contains(s) ? rate.get(s) : BigRational.ZERO;
            sentinel.delta(s, item, charging ? magnitude : magnitude.negate());
        }

        return sentinel.build();
    }

    private BigRational deliveredRateFor(Consumer c, BigRational delivered) {
        if (totalConsumption.signum() == 0) {
            return BigRational.ZERO;
        }
        if (delivered.compareTo(totalConsumption) >= 0) {
            return c.consumptionPerTick();
        }
        return switch (deficitPolicy) {
            case BLACKOUT -> BigRational.ZERO;
            case BROWNOUT -> c.consumptionPerTick().mul(delivered).div(totalConsumption);
        };
    }

}
