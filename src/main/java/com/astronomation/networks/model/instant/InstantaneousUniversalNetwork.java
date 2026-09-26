package com.astronomation.networks.model.instant;

import com.astronomation.networks.math.BigRational;
import com.astronomation.networks.model.Identifier;
import com.astronomation.networks.model.Network;
import com.astronomation.networks.model.plan.TickPlan;
import com.astronomation.networks.model.plan.TickPlanner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
    public TickPlanner planner(TickPlan.Builder builder) {
        return new InstantaneousTickPlanner(producers, consumers, storages, deficitPolicy, item, totalProduction, totalConsumption, builder);
    }

}
