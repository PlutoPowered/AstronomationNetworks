package com.astronomation.networks.benchmark;

import com.astronomation.networks.math.BigRational;
import com.astronomation.networks.math.graph.LinearMatrixBuilder;
import com.astronomation.networks.math.graph.LinearMatrixNetworkEdge;
import com.astronomation.networks.math.graph.LinearMatrixNetworkNode;
import com.astronomation.networks.math.simplex.SimplexMatrix;
import com.astronomation.networks.math.simplex.SimplexTableau;
import com.astronomation.networks.model.Identifier;
import com.astronomation.networks.model.conveyor.ConveyorNetwork;
import com.astronomation.networks.model.instant.InstantaneousUniversalNetwork;
import com.astronomation.networks.model.plan.TickPlan;
import com.astronomation.networks.model.plan.stub.StubTickPlanBuilder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("benchmark")
public class NetworkPerformanceBenchmark {

    private static final int[] SIZES = {5, 10, 20, 50, 200, 500, 1000};

    @Test
    public void benchmark_conveyorNetwork_producerMachineChain_scaling() {
        for (int size : SIZES) {
            ConveyorNetwork network = buildConveyorChain(size);

            long start = System.nanoTime();
            TickPlan plan = network.plan(new StubTickPlanBuilder());
            long elapsedNanos = System.nanoTime() - start;

            assertTrue(plan.terminalCycle().size() >= 1);
            System.out.println("ConveyorNetwork nodes=" + size + " elapsedMillis=" + (elapsedNanos / 1_000_000.0));
        }
    }

    @Test
    public void benchmark_instantaneousUniversalNetwork_producerConsumerStorage_scaling() {
        for (int size : SIZES) {
            InstantaneousUniversalNetwork network = buildInstantNetwork(size);

            long start = System.nanoTime();
            TickPlan plan = network.plan(new StubTickPlanBuilder());
            long elapsedNanos = System.nanoTime() - start;

            assertTrue(plan.terminalCycle().size() >= 1);
            System.out.println("InstantaneousUniversalNetwork nodes=" + size + " elapsedMillis=" + (elapsedNanos / 1_000_000.0));
        }
    }

    private static ConveyorNetwork buildConveyorChain(int size) {
        Identifier item = new Identifier.String("item");

        List<ConveyorNetwork.ConveyorNode> chain = new ArrayList<>();
        chain.add(new ConveyorNetwork.ConveyorNode(new Identifier.String("P"), LinearMatrixNetworkNode.Type.PRODUCER));
        for (int i = 0; i < size - 2; i++) {
            chain.add(new ConveyorNetwork.ConveyorNode(new Identifier.String("M" + i), LinearMatrixNetworkNode.Type.MACHINE));
        }
        chain.add(new ConveyorNetwork.ConveyorNode(new Identifier.String("SINK"), LinearMatrixNetworkNode.Type.SINK));

        Map<ConveyorNetwork.ConveyorNode, List<ConveyorNetwork.ConveyorEdge>> inputs = new HashMap<>();
        Map<ConveyorNetwork.ConveyorNode, List<ConveyorNetwork.ConveyorEdge>> outputs = new HashMap<>();
        for (ConveyorNetwork.ConveyorNode node : chain) {
            inputs.put(node, new ArrayList<>());
            outputs.put(node, new ArrayList<>());
        }

        for (int i = 0; i < chain.size() - 1; i++) {
            ConveyorNetwork.ConveyorNode from = chain.get(i);
            ConveyorNetwork.ConveyorNode to = chain.get(i + 1);

            BigRational outputCap = i == 0 ? BigRational.of(100) : null;
            BigRational outputQuantity = from.type() == LinearMatrixNetworkNode.Type.MACHINE ? BigRational.ONE : null;
            BigRational inputQuantity = to.type() == LinearMatrixNetworkNode.Type.MACHINE ? BigRational.ONE : null;

            outputs.get(from).add(new ConveyorNetwork.ConveyorEdge(to, item, outputCap, outputQuantity, 1));
            inputs.get(to).add(new ConveyorNetwork.ConveyorEdge(from, item, null, inputQuantity, 1));
        }

        for (ConveyorNetwork.ConveyorNode node : chain) {
            node.inputs(inputs.get(node)).outputs(outputs.get(node));
        }

        return new ConveyorNetwork(chain);
    }

    private static InstantaneousUniversalNetwork buildInstantNetwork(int size) {
        Identifier item = new Identifier.String("item");
        int third = Math.max(1, size / 3);

        List<InstantaneousUniversalNetwork.InstantNode> nodes = new ArrayList<>();
        for (int i = 0; i < third; i++) {
            nodes.add(new InstantaneousUniversalNetwork.Producer(new Identifier.String("P" + i), BigRational.of(5)));
        }
        for (int i = 0; i < third; i++) {
            nodes.add(new InstantaneousUniversalNetwork.Consumer(new Identifier.String("C" + i), BigRational.of(10)));
        }
        int storageCount = size - (2 * third);
        for (int i = 0; i < storageCount; i++) {
            nodes.add(new InstantaneousUniversalNetwork.Storage(new Identifier.String("S" + i), BigRational.ONE, BigRational.ONE, BigRational.of(i + 1), BigRational.of(1000)));
        }

        return new InstantaneousUniversalNetwork(nodes, InstantaneousUniversalNetwork.DeficitPolicy.BROWNOUT, item);
    }

}
