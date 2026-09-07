package com.astronomation.networks.model.instant;

import com.astronomation.networks.math.BigRational;
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
        String name();
    }

    public record Producer(String name, BigRational productionPerTick) implements InstantNode {
        public Producer {
            if (name == null) throw new IllegalArgumentException("name must not be null");
            if (productionPerTick == null || productionPerTick.signum() < 0)
                throw new IllegalArgumentException("productionPerTick must be >= 0");
        }
    }

    public record Consumer(String name, BigRational consumptionPerTick) implements InstantNode {
        public Consumer {
            if (name == null) throw new IllegalArgumentException("name must not be null");
            if (consumptionPerTick == null || consumptionPerTick.signum() < 0)
                throw new IllegalArgumentException("consumptionPerTick must be >= 0");
        }
    }

    public record Storage(String name, BigRational chargeRatePerTick, BigRational dischargeRatePerTick,
                           BigRational currentStorage, BigRational maxStorage) implements InstantNode {
        public Storage {
            if (name == null) throw new IllegalArgumentException("name must not be null");
            requireNonNegative(chargeRatePerTick, "chargeRatePerTick");
            requireNonNegative(dischargeRatePerTick, "dischargeRatePerTick");
            requireNonNegative(currentStorage, "currentStorage");
            requireNonNegative(maxStorage, "maxStorage");
            if (currentStorage.compareTo(maxStorage) > 0)
                throw new IllegalArgumentException("currentStorage must be <= maxStorage");
        }

        private static void requireNonNegative(BigRational value, String field) {
            if (value == null || value.signum() < 0)
                throw new IllegalArgumentException(field + " must be >= 0");
        }
    }

    public enum DeficitPolicy {
        BROWNOUT, BLACKOUT
    }

    private final List<Producer> producers;
    private final List<Consumer> consumers;
    private final List<Storage> storages;
    private final DeficitPolicy deficitPolicy;
    private final String item;
    private final BigRational totalProduction;
    private final BigRational totalConsumption;

    public InstantaneousUniversalNetwork(Collection<? extends InstantNode> nodes, DeficitPolicy deficitPolicy, String item) {
        if (nodes == null) throw new IllegalArgumentException("nodes must not be null");
        if (deficitPolicy == null) throw new IllegalArgumentException("deficitPolicy must not be null");
        if (item == null || item.isBlank()) throw new IllegalArgumentException("item must not be blank");

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
        for (Producer p : this.producers) totalProduction = totalProduction.add(p.productionPerTick());
        this.totalProduction = totalProduction;

        BigRational totalConsumption = BigRational.ZERO;
        for (Consumer c : this.consumers) totalConsumption = totalConsumption.add(c.consumptionPerTick());
        this.totalConsumption = totalConsumption;
    }

    @Override
    public TickPlan plan(TickPlan.Builder builder) {
        int cmp = totalProduction.compareTo(totalConsumption);

        List<TickPlan.Sentinel> preCycle;
        BigRational terminalDelivered;
        if (cmp > 0) {
            preCycle = runPhases(builder, true, totalProduction.sub(totalConsumption));
            terminalDelivered = totalConsumption;
        } else if (cmp < 0) {
            preCycle = runPhases(builder, false, totalConsumption.sub(totalProduction));
            terminalDelivered = totalProduction;
        } else {
            preCycle = List.of();
            terminalDelivered = totalConsumption;
        }

        for (TickPlan.Sentinel sentinel : preCycle) builder.preCycleTick(sentinel);

        Set<Storage> noActiveStorage = Set.of();
        TickPlan.Sentinel terminal = buildSentinel(builder, noActiveStorage, Map.of(), cmp >= 0, terminalDelivered);
        builder.terminalCycleTick(terminal);
        builder.terminalAverage(terminal);

        return builder.build();
    }

    private List<TickPlan.Sentinel> runPhases(TickPlan.Builder builder, boolean charging, BigRational driving) {
        List<TickPlan.Sentinel> ticks = new ArrayList<>();

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
            for (Storage s : active) totalCap = totalCap.add(charging ? s.chargeRatePerTick() : s.dischargeRatePerTick());

            BigRational totalToDistribute = BigRational.min(driving, totalCap);

            Map<Storage, BigRational> rate = new IdentityHashMap<>();
            for (Storage s : active) {
                BigRational cap = charging ? s.chargeRatePerTick() : s.dischargeRatePerTick();
                rate.put(s, cap.mul(totalToDistribute).div(totalCap));
            }

            BigRational delivered = charging ? totalConsumption : totalProduction.add(totalToDistribute);

            BigRational tMin = null;
            for (Storage s : active) tMin = BigRational.min(tMin, remaining.get(s).div(rate.get(s)));

            long wholeTicks = tMin.numerator().divide(tMin.denominator()).longValueExact();
            BigRational frac = tMin.sub(BigRational.of(wholeTicks));

            if (wholeTicks > 0) {
                TickPlan.Sentinel steady = buildSentinel(builder, active, rate, charging, delivered);
                for (long i = 0; i < wholeTicks; i++) ticks.add(steady);
                for (Storage s : active)
                    remaining.put(s, remaining.get(s).sub(rate.get(s).mul(BigRational.of(wholeTicks))));
            }

            if (frac.signum() > 0) {
                Map<Storage, BigRational> transitionRate = new IdentityHashMap<>();
                for (Storage s : active)
                    transitionRate.put(s, BigRational.min(rate.get(s), remaining.get(s)));
                ticks.add(buildSentinel(builder, active, transitionRate, charging, delivered));
                for (Storage s : active)
                    remaining.put(s, remaining.get(s).sub(transitionRate.get(s)));
            }

            active.removeIf(s -> remaining.get(s).compareTo(BigRational.ZERO) == 0);
        }

        return ticks;
    }

    private TickPlan.Sentinel buildSentinel(TickPlan.Builder builder, Set<Storage> active, Map<Storage, BigRational> rate,
                                             boolean charging, BigRational delivered) {
        TickPlan.Sentinel.Builder sentinel = builder.sentinel();

        for (Producer p : producers) sentinel.delta(p, item, p.productionPerTick());

        for (Consumer c : consumers) sentinel.delta(c, item, deliveredRateFor(c, delivered));

        for (Storage s : storages) {
            BigRational magnitude = active.contains(s) ? rate.get(s) : BigRational.ZERO;
            sentinel.delta(s, item, charging ? magnitude : magnitude.negate());
        }

        return sentinel.build();
    }

    private BigRational deliveredRateFor(Consumer c, BigRational delivered) {
        if (totalConsumption.signum() == 0) return BigRational.ZERO;
        if (delivered.compareTo(totalConsumption) >= 0) return c.consumptionPerTick();
        return switch (deficitPolicy) {
            case BLACKOUT -> BigRational.ZERO;
            case BROWNOUT -> c.consumptionPerTick().mul(delivered).div(totalConsumption);
        };
    }

}
