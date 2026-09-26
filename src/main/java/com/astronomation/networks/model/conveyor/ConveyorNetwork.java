package com.astronomation.networks.model.conveyor;

import com.astronomation.networks.math.BigRational;
import com.astronomation.networks.math.graph.LinearMatrixNetworkNode;
import com.astronomation.networks.model.Identifier;
import com.astronomation.networks.model.Network;
import com.astronomation.networks.model.plan.TickPlan;
import com.astronomation.networks.model.plan.TickPlanner;

import java.util.Collection;
import java.util.List;

public class ConveyorNetwork implements Network {

    public static final class ConveyorNode implements Network.Node {

        private final Identifier name;
        private final LinearMatrixNetworkNode.Type type;
        private List<ConveyorEdge> inputs;
        private List<ConveyorEdge> outputs;

        public ConveyorNode(Identifier name, LinearMatrixNetworkNode.Type type) {
            this.name = name;
            this.type = type;
        }

        public List<ConveyorEdge> inputs() {
            return this.inputs;
        }

        public ConveyorNode inputs(List<ConveyorEdge> inputs) {
            this.inputs = inputs;
            return this;
        }

        public List<ConveyorEdge> outputs() {
            return this.outputs;
        }

        public ConveyorNode outputs(List<ConveyorEdge> outputs) {
            this.outputs = outputs;
            return this;
        }

        public Identifier name() {
            return this.name;
        }

        public LinearMatrixNetworkNode.Type type() {
            return this.type;
        }

    }

    public static final class ConveyorEdge {

        private final ConveyorNode link;
        private final Identifier item;
        private final BigRational throughputMax;
        private final BigRational quantity;
        private final long lengthTicks;

        public ConveyorEdge(ConveyorNode link, Identifier item, BigRational throughputMax, BigRational quantity, long lengthTicks) {
            this.link = link;
            this.item = item;
            this.throughputMax = throughputMax;
            this.quantity = quantity;
            this.lengthTicks = lengthTicks;
        }

        public ConveyorNode link() {
            return this.link;
        }

        public Identifier item() {
            return this.item;
        }

        public BigRational throughputMax() {
            return this.throughputMax;
        }

        public BigRational quantity() {
            return this.quantity;
        }

        public long lengthTicks() {
            return this.lengthTicks;
        }

    }

    private final List<ConveyorNode> nodes;

    public ConveyorNetwork(Collection<? extends ConveyorNode> nodes) {
        if (nodes == null) {
            throw new IllegalArgumentException("nodes must not be null");
        }
        this.nodes = List.copyOf(nodes);
    }

    @Override
    public TickPlanner planner(TickPlan.Builder builder) {
        return new ConveyorTickPlanner(nodes, builder);
    }

}
