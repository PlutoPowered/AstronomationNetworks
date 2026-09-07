package com.astronomation.networks.model.instant;

import com.astronomation.networks.math.BigRational;
import com.astronomation.networks.model.Identifier;
import com.astronomation.networks.model.Network;
import com.astronomation.networks.model.plan.TickPlan;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class InstantaneousUniversalNetwork implements Network {

    public sealed interface InstantNode extends Network.Node permits Producer, Consumer, Storage {
        Identifier name();
    }

    public record Producer(Identifier name, BigRational productionPerTick) implements InstantNode {

    }

    public record Consumer(Identifier name, BigRational consumptionPerTick) implements InstantNode {

    }

    public record Storage(Identifier name, BigRational chargeRatePerTick, BigRational dischargeRatePerTick, BigRational currentStorage, BigRational maxStorage) implements InstantNode {

    }

    public enum DeficitPolicy {
        BROWNOUT, BLACKOUT
    }

    private final List<Producer> producers;
    private final List<Consumer> consumers;
    private final List<Storage> storages;
    private final DeficitPolicy deficitPolicy;
    private final Identifier item;
    private final BigRational totalProduction;
    private final BigRational totalConsumption;

    public InstantaneousUniversalNetwork(Collection<? extends InstantNode> nodes, DeficitPolicy deficitPolicy, Identifier item) {
        if (nodes == null) {
            throw new IllegalArgumentException("nodes must not be null");
        }
        if (deficitPolicy == null) {
            throw new IllegalArgumentException("deficitPolicy must not be null");
        }
        if (item == null) {
            throw new IllegalArgumentException("item must not be null");
        }

        List<Producer> producers = new ArrayList<>();
        List<Consumer> consumers = new ArrayList<>();
        List<Storage> storages = new ArrayList<>();
        for (InstantNode node : nodes) {
            switch (node) {
                case Producer p -> producers.add(p);
                case Consumer c -> consumers.add(c);
                case Storage s -> storages.add(s);
            }
        }

        this.producers = List.copyOf(producers);
        this.consumers = List.copyOf(consumers);
        this.storages = List.copyOf(storages);
        this.deficitPolicy = deficitPolicy;
        this.item = item;

        BigRational totalProduction = BigRational.ZERO;
        for (Producer p : this.producers) {
            totalProduction = totalProduction.add(p.productionPerTick());
        }
        this.totalProduction = totalProduction;

        BigRational totalConsumption = BigRational.ZERO;
        for (Consumer c : this.consumers) {
            totalConsumption = totalConsumption.add(c.consumptionPerTick());
        }
        this.totalConsumption = totalConsumption;
    }

    @Override
    public TickPlan plan(TickPlan.Builder builder) {
        int cmp = totalProduction.compareTo(totalConsumption);

        BigRational terminalDelivered;
        if (cmp > 0) {
            builder = runPhases(builder, true, totalProduction.sub(totalConsumption));
            terminalDelivered = totalConsumption;
        } else if (cmp < 0) {
            builder = runPhases(builder, false, totalConsumption.sub(totalProduction));
            terminalDelivered = totalProduction;
        } else {
            terminalDelivered = totalConsumption;
        }

        Set<Storage> noActiveStorage = Set.of();
        Map<Storage, BigRational> noRates = Map.of();
        builder = appendTick(builder.terminalCycleTick(), noActiveStorage, noRates, cmp >= 0, terminalDelivered);
        builder = appendTick(builder.terminalAverage(), noActiveStorage, noRates, cmp >= 0, terminalDelivered);

        return builder.build();
    }

    private TickPlan.Builder runPhases(TickPlan.Builder builder, boolean charging, BigRational driving) {
        Map<Storage, BigRational> remaining = new IdentityHashMap<>();
        Set<Storage> active = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Storage s : storages) {
            BigRational cap = charging ? s.chargeRatePerTick() : s.dischargeRatePerTick();
            BigRational boundaryRemaining = charging ? s.maxStorage().sub(s.currentStorage()) : s.currentStorage();
            if (cap.signum() > 0 && boundaryRemaining.signum() > 0) {
                remaining.put(s, boundaryRemaining);
                active.add(s);
            }
        }

        while (!active.isEmpty() && driving.signum() > 0) {
            BigRational totalCap = BigRational.ZERO;
            for (Storage s : active) {
                totalCap = totalCap.add(charging ? s.chargeRatePerTick() : s.dischargeRatePerTick());
            }

            BigRational totalToDistribute = BigRational.min(driving, totalCap);

            Map<Storage, BigRational> rate = new IdentityHashMap<>();
            for (Storage s : active) {
                BigRational cap = charging ? s.chargeRatePerTick() : s.dischargeRatePerTick();
                rate.put(s, cap.mul(totalToDistribute).div(totalCap));
            }

            BigRational delivered = charging ? totalConsumption : totalProduction.add(totalToDistribute);

            BigRational tMin = null;
            for (Storage s : active) {
                tMin = BigRational.min(tMin, remaining.get(s).div(rate.get(s)));
            }

            long wholeTicks = tMin.numerator().divide(tMin.denominator()).longValueExact();
            BigRational frac = tMin.sub(BigRational.of(wholeTicks));

            if (wholeTicks > 0) {
                for (long i = 0; i < wholeTicks; i++) {
                    builder = appendTick(builder.preCycleTick(), active, rate, charging, delivered);
                }
                for (Storage s : active) {
                    remaining.put(s, remaining.get(s).sub(rate.get(s).mul(BigRational.of(wholeTicks))));
                }
            }

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
        }

        return builder;
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
