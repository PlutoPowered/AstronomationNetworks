package com.astronomation.networks.model.conveyor;

import com.astronomation.networks.math.BigRational;
import com.astronomation.networks.math.graph.LinearMatrixNetworkNode;
import com.astronomation.networks.model.Identifier;
import com.astronomation.networks.model.plan.TickPlan;
import com.astronomation.networks.model.plan.TickPlanner;
import com.astronomation.networks.model.plan.exception.IllegalPlanStateException;
import com.astronomation.networks.model.plan.stub.StubTickPlanBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ConveyorTickPlannerTests {

    private static ConveyorNetwork line(int lengthTicks) {
        Identifier item = new Identifier.String("item");
        ConveyorNetwork.ConveyorNode p = new ConveyorNetwork.ConveyorNode(new Identifier.String("P"), LinearMatrixNetworkNode.Type.PRODUCER);
        ConveyorNetwork.ConveyorNode sink = new ConveyorNetwork.ConveyorNode(new Identifier.String("SINK"), LinearMatrixNetworkNode.Type.SINK);
        p.inputs(List.of()).outputs(List.of(new ConveyorNetwork.ConveyorEdge(sink, item, BigRational.of(10), null, lengthTicks)));
        sink.inputs(List.of(new ConveyorNetwork.ConveyorEdge(p, item, null, null, lengthTicks))).outputs(List.of());
        return new ConveyorNetwork(List.of(p, sink));
    }

    @Test
    public void test_manualIteration_matchesBlockingPlan() {
        ConveyorNetwork network = line(3);
        TickPlan expected = network.plan(new StubTickPlanBuilder());

        TickPlanner planner = network.planner(new StubTickPlanBuilder());
        int steps = 1;
        while (planner.iterate()) {
            steps++;
        }
        TickPlan actual = planner.plan();

        assertTrue(steps > 1);
        assertEquals(expected.preCycle().size(), actual.preCycle().size());
        assertEquals(expected.terminalCycle().size(), actual.terminalCycle().size());
    }

    @Test
    public void test_planBeforeFinished_throws() {
        TickPlanner planner = line(3).planner(new StubTickPlanBuilder());
        planner.iterate();
        assertThrows(IllegalPlanStateException.class, planner::plan);
    }

    @Test
    public void test_iterateAfterFinished_returnsFalse() {
        TickPlanner planner = line(0).planner(new StubTickPlanBuilder());
        while (planner.iterate());
        assertFalse(planner.iterate());
        assertNotNull(planner.plan());
    }

}
