package com.astronomation.networks.model.instant;

import com.astronomation.networks.math.BigRational;
import com.astronomation.networks.model.Identifier;
import com.astronomation.networks.model.plan.TickPlan;
import com.astronomation.networks.model.plan.TickPlanner;
import com.astronomation.networks.model.plan.exception.IllegalPlanStateException;
import com.astronomation.networks.model.plan.stub.StubTickPlanBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class InstantaneousTickPlannerTests {

    private static InstantaneousUniversalNetwork chargingNetwork() {
        InstantaneousUniversalNetwork.Producer p = new InstantaneousUniversalNetwork.Producer(new Identifier.String("P"), BigRational.of(10));
        InstantaneousUniversalNetwork.Consumer c = new InstantaneousUniversalNetwork.Consumer(new Identifier.String("C"), BigRational.of(6));
        InstantaneousUniversalNetwork.Storage s = new InstantaneousUniversalNetwork.Storage(new Identifier.String("S"), BigRational.of(2), BigRational.of(1), BigRational.of(0), BigRational.of(8));
        return new InstantaneousUniversalNetwork(List.of(p, c, s), InstantaneousUniversalNetwork.DeficitPolicy.BROWNOUT, new Identifier.String("power"));
    }

    @Test
    public void test_manualIteration_matchesBlockingPlan() {
        InstantaneousUniversalNetwork network = chargingNetwork();
        TickPlan expected = network.plan(new StubTickPlanBuilder());

        TickPlanner planner = network.planner(new StubTickPlanBuilder());
        int steps = 1;
        while (planner.iterate()) {
            steps++;
        }
        TickPlan actual = planner.plan();

        assertTrue(steps > expected.preCycle().size());
        assertEquals(4, actual.preCycle().size());
        assertEquals(expected.terminalCycle().size(), actual.terminalCycle().size());
    }

    @Test
    public void test_planBeforeFinished_throws() {
        TickPlanner planner = chargingNetwork().planner(new StubTickPlanBuilder());
        planner.iterate();
        assertThrows(IllegalPlanStateException.class, planner::plan);
    }

    @Test
    public void test_iterateAfterFinished_returnsFalse() {
        TickPlanner planner = chargingNetwork().planner(new StubTickPlanBuilder());
        while (planner.iterate());
        assertFalse(planner.iterate());
        assertNotNull(planner.plan());
    }

}
