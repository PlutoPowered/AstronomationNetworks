package com.astronomation.networks.model.instant;

import com.astronomation.networks.math.BigRational;
import com.astronomation.networks.model.Identifier;
import com.astronomation.networks.model.Network;
import com.astronomation.networks.model.plan.TickPlan;
import com.astronomation.networks.model.plan.stub.StubTickPlanBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class InstantaneousUniversalNetworkTests {

    private static BigRational quantity(TickPlan.Sentinel sentinel, Network.Node node) {
        Set<TickPlan.Delta> deltas = sentinel.deltas(node);
        assertEquals(1, deltas.size());
        return deltas.iterator().next().quantity();
    }

    @Test
    public void test_balanced_noStorage_immediateSteady_2node() {
        InstantaneousUniversalNetwork.Producer p = new InstantaneousUniversalNetwork.Producer(new Identifier.String("P"), BigRational.of(10));
        InstantaneousUniversalNetwork.Consumer c = new InstantaneousUniversalNetwork.Consumer(new Identifier.String("C"), BigRational.of(10));

        InstantaneousUniversalNetwork network = new InstantaneousUniversalNetwork(
                List.of(p, c), InstantaneousUniversalNetwork.DeficitPolicy.BROWNOUT, new Identifier.String("power"));

        TickPlan plan = network.plan(new StubTickPlanBuilder());

        assertTrue(plan.preCycle().isEmpty());
        assertEquals(1, plan.terminalCycle().size());

        TickPlan.Sentinel terminal = plan.terminalCycle().get(0);
        assertEquals(BigRational.of(10), quantity(terminal, p));
        assertEquals(BigRational.of(10), quantity(terminal, c));
    }

    @Test
    public void test_surplus_singleStorage_chargesToFull_thenSteady_3node() {
        InstantaneousUniversalNetwork.Producer p = new InstantaneousUniversalNetwork.Producer(new Identifier.String("P"), BigRational.of(10));
        InstantaneousUniversalNetwork.Consumer c = new InstantaneousUniversalNetwork.Consumer(new Identifier.String("C"), BigRational.of(6));
        InstantaneousUniversalNetwork.Storage s = new InstantaneousUniversalNetwork.Storage(
                new Identifier.String("S"), BigRational.of(2), BigRational.of(1), BigRational.of(0), BigRational.of(8));

        InstantaneousUniversalNetwork network = new InstantaneousUniversalNetwork(
                List.of(p, c, s), InstantaneousUniversalNetwork.DeficitPolicy.BROWNOUT, new Identifier.String("power"));

        TickPlan plan = network.plan(new StubTickPlanBuilder());

        assertEquals(4, plan.preCycle().size());
        for (TickPlan.Sentinel tick : plan.preCycle()) {
            assertEquals(BigRational.of(10), quantity(tick, p));
            assertEquals(BigRational.of(6), quantity(tick, c));
            assertEquals(BigRational.of(2), quantity(tick, s));
        }

        TickPlan.Sentinel terminal = plan.terminalCycle().get(0);
        assertEquals(BigRational.ZERO, quantity(terminal, s));
        assertEquals(BigRational.of(6), quantity(terminal, c));
    }

    @Test
    public void test_deficit_singleStorage_fullyCoversDeficitWhileDraining_thenBrownout_3node() {
        InstantaneousUniversalNetwork.Producer p = new InstantaneousUniversalNetwork.Producer(new Identifier.String("P"), BigRational.of(2));
        InstantaneousUniversalNetwork.Consumer c = new InstantaneousUniversalNetwork.Consumer(new Identifier.String("C"), BigRational.of(5));
        InstantaneousUniversalNetwork.Storage s = new InstantaneousUniversalNetwork.Storage(
                new Identifier.String("S"), BigRational.of(1), BigRational.of(5), BigRational.of(6), BigRational.of(20));

        InstantaneousUniversalNetwork network = new InstantaneousUniversalNetwork(
                List.of(p, c, s), InstantaneousUniversalNetwork.DeficitPolicy.BROWNOUT, new Identifier.String("power"));

        TickPlan plan = network.plan(new StubTickPlanBuilder());

        assertEquals(2, plan.preCycle().size());
        for (TickPlan.Sentinel tick : plan.preCycle()) {
            assertEquals(BigRational.of(5), quantity(tick, c));
            assertEquals(BigRational.of(-3), quantity(tick, s));
        }

        TickPlan.Sentinel terminal = plan.terminalCycle().get(0);
        assertEquals(BigRational.of(2), quantity(terminal, c));
        assertEquals(BigRational.ZERO, quantity(terminal, s));
    }

    @Test
    public void test_deficit_singleStorage_blackoutVariant_3node() {
        InstantaneousUniversalNetwork.Producer p = new InstantaneousUniversalNetwork.Producer(new Identifier.String("P"), BigRational.of(2));
        InstantaneousUniversalNetwork.Consumer c = new InstantaneousUniversalNetwork.Consumer(new Identifier.String("C"), BigRational.of(5));
        InstantaneousUniversalNetwork.Storage s = new InstantaneousUniversalNetwork.Storage(
                new Identifier.String("S"), BigRational.of(1), BigRational.of(5), BigRational.of(6), BigRational.of(20));

        InstantaneousUniversalNetwork network = new InstantaneousUniversalNetwork(
                List.of(p, c, s), InstantaneousUniversalNetwork.DeficitPolicy.BLACKOUT, new Identifier.String("power"));

        TickPlan plan = network.plan(new StubTickPlanBuilder());

        assertEquals(2, plan.preCycle().size());
        for (TickPlan.Sentinel tick : plan.preCycle()) {
            assertEquals(BigRational.of(5), quantity(tick, c));
        }

        TickPlan.Sentinel terminal = plan.terminalCycle().get(0);
        assertEquals(BigRational.ZERO, quantity(terminal, c));
    }

    @Test
    public void test_deficit_capacityBelowDeficit_throttlesDuringPreCycle_3node() {
        InstantaneousUniversalNetwork.Producer p = new InstantaneousUniversalNetwork.Producer(new Identifier.String("P"), BigRational.of(4));
        InstantaneousUniversalNetwork.Consumer c = new InstantaneousUniversalNetwork.Consumer(new Identifier.String("C"), BigRational.of(10));
        InstantaneousUniversalNetwork.Storage s = new InstantaneousUniversalNetwork.Storage(
                new Identifier.String("S"), BigRational.of(1), BigRational.of(3), BigRational.of(9), BigRational.of(20));

        InstantaneousUniversalNetwork network = new InstantaneousUniversalNetwork(
                List.of(p, c, s), InstantaneousUniversalNetwork.DeficitPolicy.BROWNOUT, new Identifier.String("power"));

        TickPlan plan = network.plan(new StubTickPlanBuilder());

        assertEquals(3, plan.preCycle().size());
        for (TickPlan.Sentinel tick : plan.preCycle()) {
            assertEquals(BigRational.of(7), quantity(tick, c));
            assertEquals(BigRational.of(-3), quantity(tick, s));
        }

        TickPlan.Sentinel terminal = plan.terminalCycle().get(0);
        assertEquals(BigRational.of(4), quantity(terminal, c));
    }

    @Test
    public void test_multipleStorages_proportionalSplitCharging_phaseTransition_4node() {
        InstantaneousUniversalNetwork.Producer p = new InstantaneousUniversalNetwork.Producer(new Identifier.String("P"), BigRational.of(20));
        InstantaneousUniversalNetwork.Consumer c = new InstantaneousUniversalNetwork.Consumer(new Identifier.String("C"), BigRational.of(5));
        InstantaneousUniversalNetwork.Storage a = new InstantaneousUniversalNetwork.Storage(
                new Identifier.String("A"), BigRational.of(2), BigRational.of(1), BigRational.of(0), BigRational.of(4));
        InstantaneousUniversalNetwork.Storage b = new InstantaneousUniversalNetwork.Storage(
                new Identifier.String("B"), BigRational.of(4), BigRational.of(1), BigRational.of(0), BigRational.of(4));

        InstantaneousUniversalNetwork network = new InstantaneousUniversalNetwork(
                List.of(p, c, a, b), InstantaneousUniversalNetwork.DeficitPolicy.BROWNOUT, new Identifier.String("power"));

        TickPlan plan = network.plan(new StubTickPlanBuilder());

        assertEquals(2, plan.preCycle().size());

        TickPlan.Sentinel tick1 = plan.preCycle().get(0);
        assertEquals(BigRational.of(2), quantity(tick1, a));
        assertEquals(BigRational.of(4), quantity(tick1, b));
        assertEquals(BigRational.of(5), quantity(tick1, c));

        TickPlan.Sentinel tick2 = plan.preCycle().get(1);
        assertEquals(BigRational.of(2), quantity(tick2, a));
        assertEquals(BigRational.ZERO, quantity(tick2, b));
        assertEquals(BigRational.of(5), quantity(tick2, c));

        TickPlan.Sentinel terminal = plan.terminalCycle().get(0);
        assertEquals(BigRational.ZERO, quantity(terminal, a));
        assertEquals(BigRational.ZERO, quantity(terminal, b));
    }

    @Test
    public void test_multipleStorages_proportionalSplitDischarging_phaseTransition_4node() {
        InstantaneousUniversalNetwork.Producer p = new InstantaneousUniversalNetwork.Producer(new Identifier.String("P"), BigRational.of(2));
        InstantaneousUniversalNetwork.Consumer c = new InstantaneousUniversalNetwork.Consumer(new Identifier.String("C"), BigRational.of(20));
        InstantaneousUniversalNetwork.Storage a = new InstantaneousUniversalNetwork.Storage(
                new Identifier.String("A"), BigRational.of(1), BigRational.of(2), BigRational.of(4), BigRational.of(10));
        InstantaneousUniversalNetwork.Storage b = new InstantaneousUniversalNetwork.Storage(
                new Identifier.String("B"), BigRational.of(1), BigRational.of(3), BigRational.of(9), BigRational.of(10));

        InstantaneousUniversalNetwork network = new InstantaneousUniversalNetwork(
                List.of(p, c, a, b), InstantaneousUniversalNetwork.DeficitPolicy.BROWNOUT, new Identifier.String("power"));

        TickPlan plan = network.plan(new StubTickPlanBuilder());

        assertEquals(3, plan.preCycle().size());

        TickPlan.Sentinel tick1 = plan.preCycle().get(0);
        assertEquals(BigRational.of(-2), quantity(tick1, a));
        assertEquals(BigRational.of(-3), quantity(tick1, b));
        assertEquals(BigRational.of(7), quantity(tick1, c));

        TickPlan.Sentinel tick2 = plan.preCycle().get(1);
        assertEquals(BigRational.of(-2), quantity(tick2, a));
        assertEquals(BigRational.of(-3), quantity(tick2, b));
        assertEquals(BigRational.of(7), quantity(tick2, c));

        TickPlan.Sentinel tick3 = plan.preCycle().get(2);
        assertEquals(BigRational.ZERO, quantity(tick3, a));
        assertEquals(BigRational.of(-3), quantity(tick3, b));
        assertEquals(BigRational.of(5), quantity(tick3, c));

        TickPlan.Sentinel terminal = plan.terminalCycle().get(0);
        assertEquals(BigRational.of(2), quantity(terminal, c));
    }

    @Test
    public void test_storage_startsPartiallyCharged_honoredAsInput_4node() {
        InstantaneousUniversalNetwork.Producer p = new InstantaneousUniversalNetwork.Producer(new Identifier.String("P"), BigRational.of(10));
        InstantaneousUniversalNetwork.Consumer c = new InstantaneousUniversalNetwork.Consumer(new Identifier.String("C"), BigRational.of(6));
        InstantaneousUniversalNetwork.Storage s = new InstantaneousUniversalNetwork.Storage(
                new Identifier.String("S"), BigRational.of(4), BigRational.of(2), BigRational.of(5), BigRational.of(9));

        InstantaneousUniversalNetwork network = new InstantaneousUniversalNetwork(
                List.of(p, c, s), InstantaneousUniversalNetwork.DeficitPolicy.BROWNOUT, new Identifier.String("power"));

        TickPlan plan = network.plan(new StubTickPlanBuilder());

        assertEquals(1, plan.preCycle().size());
        assertEquals(BigRational.of(4), quantity(plan.preCycle().get(0), s));

        TickPlan.Sentinel terminal = plan.terminalCycle().get(0);
        assertEquals(BigRational.ZERO, quantity(terminal, s));
    }

    @Test
    public void test_exactIntegerTickBoundary_noTransitionTick_3node() {
        InstantaneousUniversalNetwork.Producer p = new InstantaneousUniversalNetwork.Producer(new Identifier.String("P"), BigRational.of(10));
        InstantaneousUniversalNetwork.Consumer c = new InstantaneousUniversalNetwork.Consumer(new Identifier.String("C"), BigRational.of(6));
        InstantaneousUniversalNetwork.Storage s = new InstantaneousUniversalNetwork.Storage(
                new Identifier.String("S"), BigRational.of(2), BigRational.of(1), BigRational.of(0), BigRational.of(8));

        InstantaneousUniversalNetwork network = new InstantaneousUniversalNetwork(
                List.of(p, c, s), InstantaneousUniversalNetwork.DeficitPolicy.BROWNOUT, new Identifier.String("power"));

        TickPlan plan = network.plan(new StubTickPlanBuilder());

        assertEquals(4, plan.preCycle().size());
        for (TickPlan.Sentinel tick : plan.preCycle()) {
            assertEquals(BigRational.of(2), quantity(tick, s));
        }
    }

    @Test
    public void test_fractionalTickBoundary_exactTransitionRemainder_3node() {
        InstantaneousUniversalNetwork.Producer p = new InstantaneousUniversalNetwork.Producer(new Identifier.String("P"), BigRational.of(10));
        InstantaneousUniversalNetwork.Consumer c = new InstantaneousUniversalNetwork.Consumer(new Identifier.String("C"), BigRational.of(3));
        InstantaneousUniversalNetwork.Storage s = new InstantaneousUniversalNetwork.Storage(
                new Identifier.String("S"), BigRational.of(3), BigRational.of(1), BigRational.of(0), BigRational.of(10));

        InstantaneousUniversalNetwork network = new InstantaneousUniversalNetwork(
                List.of(p, c, s), InstantaneousUniversalNetwork.DeficitPolicy.BROWNOUT, new Identifier.String("power"));

        TickPlan plan = network.plan(new StubTickPlanBuilder());

        assertEquals(4, plan.preCycle().size());
        assertEquals(BigRational.of(3), quantity(plan.preCycle().get(0), s));
        assertEquals(BigRational.of(3), quantity(plan.preCycle().get(1), s));
        assertEquals(BigRational.of(3), quantity(plan.preCycle().get(2), s));
        assertEquals(BigRational.of(1), quantity(plan.preCycle().get(3), s));

        TickPlan.Sentinel terminal = plan.terminalCycle().get(0);
        assertEquals(BigRational.ZERO, quantity(terminal, s));
    }

    @Test
    public void test_deficit_noStorage_brownout_2node() {
        InstantaneousUniversalNetwork.Producer p = new InstantaneousUniversalNetwork.Producer(new Identifier.String("P"), BigRational.of(3));
        InstantaneousUniversalNetwork.Consumer c = new InstantaneousUniversalNetwork.Consumer(new Identifier.String("C"), BigRational.of(10));

        InstantaneousUniversalNetwork network = new InstantaneousUniversalNetwork(
                List.of(p, c), InstantaneousUniversalNetwork.DeficitPolicy.BROWNOUT, new Identifier.String("power"));

        TickPlan plan = network.plan(new StubTickPlanBuilder());

        assertTrue(plan.preCycle().isEmpty());
        TickPlan.Sentinel terminal = plan.terminalCycle().get(0);
        assertEquals(BigRational.of(3), quantity(terminal, c));
    }

    @Test
    public void test_deficit_noStorage_blackout_2node() {
        InstantaneousUniversalNetwork.Producer p = new InstantaneousUniversalNetwork.Producer(new Identifier.String("P"), BigRational.of(3));
        InstantaneousUniversalNetwork.Consumer c = new InstantaneousUniversalNetwork.Consumer(new Identifier.String("C"), BigRational.of(10));

        InstantaneousUniversalNetwork network = new InstantaneousUniversalNetwork(
                List.of(p, c), InstantaneousUniversalNetwork.DeficitPolicy.BLACKOUT, new Identifier.String("power"));

        TickPlan plan = network.plan(new StubTickPlanBuilder());

        assertTrue(plan.preCycle().isEmpty());
        TickPlan.Sentinel terminal = plan.terminalCycle().get(0);
        assertEquals(BigRational.ZERO, quantity(terminal, c));
    }

    @Test
    public void test_terminalAverage_equalsTerminalCycleSentinel_2node() {
        InstantaneousUniversalNetwork.Producer p = new InstantaneousUniversalNetwork.Producer(new Identifier.String("P"), BigRational.of(7));
        InstantaneousUniversalNetwork.Consumer c = new InstantaneousUniversalNetwork.Consumer(new Identifier.String("C"), BigRational.of(7));

        InstantaneousUniversalNetwork network = new InstantaneousUniversalNetwork(
                List.of(p, c), InstantaneousUniversalNetwork.DeficitPolicy.BROWNOUT, new Identifier.String("power"));

        TickPlan plan = network.plan(new StubTickPlanBuilder());

        TickPlan.Sentinel terminal = plan.terminalCycle().get(0);
        TickPlan.Sentinel average = plan.terminalAverage();

        assertEquals(quantity(terminal, p), quantity(average, p));
        assertEquals(quantity(terminal, c), quantity(average, c));
    }

}
