package com.astronomation.networks.model.conveyor;

import com.astronomation.networks.math.BigRational;
import com.astronomation.networks.math.graph.LinearMatrixNetworkNode;
import com.astronomation.networks.model.Identifier;
import com.astronomation.networks.model.Network;
import com.astronomation.networks.model.plan.TickPlan;
import com.astronomation.networks.model.plan.stub.StubTickPlanBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ConveyorNetworkTests {

    private static BigRational quantity(TickPlan.Sentinel sentinel, Network.Node node, Identifier item) {
        for (TickPlan.Delta delta : sentinel.deltas(node)) {
            if (delta.item().equals(item)) {
                return delta.quantity();
            }
        }
        return BigRational.ZERO;
    }

    @Test
    public void test_producerToSink_transportDelay_preCycleThenSteady_2node() {
        Identifier item = new Identifier.String("item");
        ConveyorNetwork.ConveyorNode p = new ConveyorNetwork.ConveyorNode(new Identifier.String("P"), LinearMatrixNetworkNode.Type.PRODUCER);
        ConveyorNetwork.ConveyorNode sink = new ConveyorNetwork.ConveyorNode(new Identifier.String("SINK"), LinearMatrixNetworkNode.Type.SINK);

        ConveyorNetwork.ConveyorEdge pOut = new ConveyorNetwork.ConveyorEdge(sink, item, BigRational.of(10), null, 3);
        ConveyorNetwork.ConveyorEdge sinkIn = new ConveyorNetwork.ConveyorEdge(p, item, null, null, 3);

        p.inputs(List.of()).outputs(List.of(pOut));
        sink.inputs(List.of(sinkIn)).outputs(List.of());

        ConveyorNetwork network = new ConveyorNetwork(List.of(p, sink));
        TickPlan plan = network.plan(new StubTickPlanBuilder());

        assertEquals(3, plan.preCycle().size());
        for (TickPlan.Sentinel tick : plan.preCycle()) {
            assertEquals(BigRational.of(10), quantity(tick, p, item));
            assertEquals(BigRational.ZERO, quantity(tick, sink, item));
        }

        TickPlan.Sentinel terminal = plan.terminalCycle().get(0);
        assertEquals(BigRational.of(10), quantity(terminal, p, item));
        assertEquals(BigRational.of(10), quantity(terminal, sink, item));
        assertEquals(quantity(terminal, sink, item), quantity(plan.terminalAverage(), sink, item));
    }

    @Test
    public void test_zeroLengthEdges_immediateSteady_2node() {
        Identifier item = new Identifier.String("item");
        ConveyorNetwork.ConveyorNode p = new ConveyorNetwork.ConveyorNode(new Identifier.String("P"), LinearMatrixNetworkNode.Type.PRODUCER);
        ConveyorNetwork.ConveyorNode sink = new ConveyorNetwork.ConveyorNode(new Identifier.String("SINK"), LinearMatrixNetworkNode.Type.SINK);

        ConveyorNetwork.ConveyorEdge pOut = new ConveyorNetwork.ConveyorEdge(sink, item, BigRational.of(7), null, 0);
        ConveyorNetwork.ConveyorEdge sinkIn = new ConveyorNetwork.ConveyorEdge(p, item, null, null, 0);

        p.inputs(List.of()).outputs(List.of(pOut));
        sink.inputs(List.of(sinkIn)).outputs(List.of());

        ConveyorNetwork network = new ConveyorNetwork(List.of(p, sink));
        TickPlan plan = network.plan(new StubTickPlanBuilder());

        assertTrue(plan.preCycle().isEmpty());
        TickPlan.Sentinel terminal = plan.terminalCycle().get(0);
        assertEquals(BigRational.of(7), quantity(terminal, p, item));
        assertEquals(BigRational.of(7), quantity(terminal, sink, item));
    }

    @Test
    public void test_machine_gatedOnSlowestRequiredInput_notFasterEdgeArrival_3node() {
        Identifier ore = new Identifier.String("ore");
        Identifier coal = new Identifier.String("coal");
        Identifier plate = new Identifier.String("plate");

        ConveyorNetwork.ConveyorNode p1 = new ConveyorNetwork.ConveyorNode(new Identifier.String("P1"), LinearMatrixNetworkNode.Type.PRODUCER);
        ConveyorNetwork.ConveyorNode p2 = new ConveyorNetwork.ConveyorNode(new Identifier.String("P2"), LinearMatrixNetworkNode.Type.PRODUCER);
        ConveyorNetwork.ConveyorNode m = new ConveyorNetwork.ConveyorNode(new Identifier.String("M"), LinearMatrixNetworkNode.Type.MACHINE);
        ConveyorNetwork.ConveyorNode sink = new ConveyorNetwork.ConveyorNode(new Identifier.String("SINK"), LinearMatrixNetworkNode.Type.SINK);

        ConveyorNetwork.ConveyorEdge p1Out = new ConveyorNetwork.ConveyorEdge(m, ore, BigRational.of(10), null, 2);
        ConveyorNetwork.ConveyorEdge p2Out = new ConveyorNetwork.ConveyorEdge(m, coal, BigRational.of(10), null, 5);
        ConveyorNetwork.ConveyorEdge mInOre = new ConveyorNetwork.ConveyorEdge(p1, ore, null, BigRational.of(1), 2);
        ConveyorNetwork.ConveyorEdge mInCoal = new ConveyorNetwork.ConveyorEdge(p2, coal, null, BigRational.of(1), 5);
        ConveyorNetwork.ConveyorEdge mOut = new ConveyorNetwork.ConveyorEdge(sink, plate, null, BigRational.of(1), 1);
        ConveyorNetwork.ConveyorEdge sinkIn = new ConveyorNetwork.ConveyorEdge(m, plate, null, null, 1);

        p1.inputs(List.of()).outputs(List.of(p1Out));
        p2.inputs(List.of()).outputs(List.of(p2Out));
        m.inputs(List.of(mInOre, mInCoal)).outputs(List.of(mOut));
        sink.inputs(List.of(sinkIn)).outputs(List.of());

        ConveyorNetwork network = new ConveyorNetwork(List.of(p1, p2, m, sink));
        TickPlan plan = network.plan(new StubTickPlanBuilder());

        assertEquals(6, plan.preCycle().size());
        for (int tick = 0; tick < 5; tick++) {
            TickPlan.Sentinel sentinel = plan.preCycle().get(tick);
            assertEquals(BigRational.of(10), quantity(sentinel, p1, ore));
            assertEquals(BigRational.of(10), quantity(sentinel, p2, coal));
            assertEquals(BigRational.ZERO, quantity(sentinel, m, ore));
            assertEquals(BigRational.ZERO, quantity(sentinel, m, coal));
            assertEquals(BigRational.ZERO, quantity(sentinel, m, plate));
            assertEquals(BigRational.ZERO, quantity(sentinel, sink, plate));
        }

        TickPlan.Sentinel tick5 = plan.preCycle().get(5);
        assertEquals(BigRational.of(10), quantity(tick5, m, ore));
        assertEquals(BigRational.of(10), quantity(tick5, m, coal));
        assertEquals(BigRational.of(10), quantity(tick5, m, plate));
        assertEquals(BigRational.ZERO, quantity(tick5, sink, plate));

        TickPlan.Sentinel terminal = plan.terminalCycle().get(0);
        assertEquals(BigRational.of(10), quantity(terminal, sink, plate));
    }

    @Test
    public void test_merger_differentBranchLengths_sumsAlongCriticalPath_3node() {
        Identifier item = new Identifier.String("item");

        ConveyorNetwork.ConveyorNode p1 = new ConveyorNetwork.ConveyorNode(new Identifier.String("P1"), LinearMatrixNetworkNode.Type.PRODUCER);
        ConveyorNetwork.ConveyorNode p2 = new ConveyorNetwork.ConveyorNode(new Identifier.String("P2"), LinearMatrixNetworkNode.Type.PRODUCER);
        ConveyorNetwork.ConveyorNode mer = new ConveyorNetwork.ConveyorNode(new Identifier.String("MER"), LinearMatrixNetworkNode.Type.MERGER);
        ConveyorNetwork.ConveyorNode sink = new ConveyorNetwork.ConveyorNode(new Identifier.String("SINK"), LinearMatrixNetworkNode.Type.SINK);

        ConveyorNetwork.ConveyorEdge p1Out = new ConveyorNetwork.ConveyorEdge(mer, item, BigRational.of(6), null, 4);
        ConveyorNetwork.ConveyorEdge p2Out = new ConveyorNetwork.ConveyorEdge(mer, item, BigRational.of(9), null, 1);
        ConveyorNetwork.ConveyorEdge merIn1 = new ConveyorNetwork.ConveyorEdge(p1, item, null, null, 4);
        ConveyorNetwork.ConveyorEdge merIn2 = new ConveyorNetwork.ConveyorEdge(p2, item, null, null, 1);
        ConveyorNetwork.ConveyorEdge merOut = new ConveyorNetwork.ConveyorEdge(sink, item, null, null, 2);
        ConveyorNetwork.ConveyorEdge sinkIn = new ConveyorNetwork.ConveyorEdge(mer, item, null, null, 2);

        p1.inputs(List.of()).outputs(List.of(p1Out));
        p2.inputs(List.of()).outputs(List.of(p2Out));
        mer.inputs(List.of(merIn1, merIn2)).outputs(List.of(merOut));
        sink.inputs(List.of(sinkIn)).outputs(List.of());

        ConveyorNetwork network = new ConveyorNetwork(List.of(p1, p2, mer, sink));
        TickPlan plan = network.plan(new StubTickPlanBuilder());

        assertEquals(6, plan.preCycle().size());
        for (int tick = 0; tick < 4; tick++) {
            TickPlan.Sentinel sentinel = plan.preCycle().get(tick);
            assertEquals(BigRational.ZERO, quantity(sentinel, mer, item));
        }
        for (int tick = 4; tick < 6; tick++) {
            TickPlan.Sentinel sentinel = plan.preCycle().get(tick);
            assertEquals(BigRational.of(15), quantity(sentinel, mer, item));
            assertEquals(BigRational.ZERO, quantity(sentinel, sink, item));
        }

        TickPlan.Sentinel terminal = plan.terminalCycle().get(0);
        assertEquals(BigRational.of(6), quantity(terminal, p1, item));
        assertEquals(BigRational.of(9), quantity(terminal, p2, item));
        assertEquals(BigRational.of(15), quantity(terminal, mer, item));
        assertEquals(BigRational.of(15), quantity(terminal, sink, item));
    }

    @Test
    public void test_cycle_zeroFlowLoop_succeeds_3node() {
        Identifier ore = new Identifier.String("ore");
        Identifier plate = new Identifier.String("plate");
        Identifier junk = new Identifier.String("junk");

        ConveyorNetwork.ConveyorNode p = new ConveyorNetwork.ConveyorNode(new Identifier.String("P"), LinearMatrixNetworkNode.Type.PRODUCER);
        ConveyorNetwork.ConveyorNode m = new ConveyorNetwork.ConveyorNode(new Identifier.String("M"), LinearMatrixNetworkNode.Type.MACHINE);
        ConveyorNetwork.ConveyorNode sink = new ConveyorNetwork.ConveyorNode(new Identifier.String("SINK"), LinearMatrixNetworkNode.Type.SINK);

        ConveyorNetwork.ConveyorEdge pOut = new ConveyorNetwork.ConveyorEdge(m, ore, BigRational.of(8), null, 3);
        ConveyorNetwork.ConveyorEdge mInOre = new ConveyorNetwork.ConveyorEdge(p, ore, null, BigRational.of(1), 3);
        ConveyorNetwork.ConveyorEdge mInLoop = new ConveyorNetwork.ConveyorEdge(m, junk, null, null, 5);
        ConveyorNetwork.ConveyorEdge mOutLoop = new ConveyorNetwork.ConveyorEdge(m, junk, null, null, 5);
        ConveyorNetwork.ConveyorEdge mOutPlate = new ConveyorNetwork.ConveyorEdge(sink, plate, null, BigRational.of(1), 2);
        ConveyorNetwork.ConveyorEdge sinkIn = new ConveyorNetwork.ConveyorEdge(m, plate, null, null, 2);

        p.inputs(List.of()).outputs(List.of(pOut));
        m.inputs(List.of(mInOre, mInLoop)).outputs(List.of(mOutLoop, mOutPlate));
        sink.inputs(List.of(sinkIn)).outputs(List.of());

        ConveyorNetwork network = new ConveyorNetwork(List.of(p, m, sink));
        TickPlan plan = network.plan(new StubTickPlanBuilder());

        assertEquals(5, plan.preCycle().size());
        for (TickPlan.Sentinel tick : plan.preCycle()) {
            assertEquals(BigRational.ZERO, quantity(tick, m, junk));
        }
        for (int tick = 0; tick < 3; tick++) {
            assertEquals(BigRational.ZERO, quantity(plan.preCycle().get(tick), m, ore));
        }
        for (int tick = 3; tick < 5; tick++) {
            assertEquals(BigRational.of(8), quantity(plan.preCycle().get(tick), m, ore));
            assertEquals(BigRational.ZERO, quantity(plan.preCycle().get(tick), sink, plate));
        }

        TickPlan.Sentinel terminal = plan.terminalCycle().get(0);
        assertEquals(BigRational.of(8), quantity(terminal, m, plate));
        assertEquals(BigRational.of(8), quantity(terminal, sink, plate));
        assertEquals(BigRational.ZERO, quantity(terminal, m, junk));
    }

    @Test
    public void test_cycle_positiveFlowLoop_throws_2node() {
        Identifier ore = new Identifier.String("ore");
        Identifier loop = new Identifier.String("loop");

        ConveyorNetwork.ConveyorNode p = new ConveyorNetwork.ConveyorNode(new Identifier.String("P"), LinearMatrixNetworkNode.Type.PRODUCER);
        ConveyorNetwork.ConveyorNode m = new ConveyorNetwork.ConveyorNode(new Identifier.String("M"), LinearMatrixNetworkNode.Type.MACHINE);

        ConveyorNetwork.ConveyorEdge pOut = new ConveyorNetwork.ConveyorEdge(m, ore, BigRational.of(10), null, 1);
        ConveyorNetwork.ConveyorEdge mInOre = new ConveyorNetwork.ConveyorEdge(p, ore, null, BigRational.of(1), 1);
        ConveyorNetwork.ConveyorEdge mInLoop = new ConveyorNetwork.ConveyorEdge(m, loop, null, BigRational.of(1), 1);
        ConveyorNetwork.ConveyorEdge mOutLoop = new ConveyorNetwork.ConveyorEdge(m, loop, null, BigRational.of(1), 1);

        p.inputs(List.of()).outputs(List.of(pOut));
        m.inputs(List.of(mInOre, mInLoop)).outputs(List.of(mOutLoop));

        ConveyorNetwork network = new ConveyorNetwork(List.of(p, m));

        assertThrows(IllegalStateException.class, () -> network.plan(new StubTickPlanBuilder()));
    }

}
