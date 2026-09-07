package com.astronomation.networks.math.graph;

import java.util.List;

public class LinearMatrixNetworkNode {
    private String name;
    private Type type;
    private List<LinearMatrixNetworkEdge> inputs;
    private List<LinearMatrixNetworkEdge> outputs;

    public LinearMatrixNetworkNode(String name, Type type) {
        this.name = name;
        this.type = type;
    }

    public List<LinearMatrixNetworkEdge> inputs() {
        return this.inputs;
    }

    public LinearMatrixNetworkNode inputs(List<LinearMatrixNetworkEdge> inputs) {
        this.inputs = inputs;
        return this;
    }

    public List<LinearMatrixNetworkEdge> outputs() {
        return this.outputs;
    }

    public LinearMatrixNetworkNode outputs(List<LinearMatrixNetworkEdge> outputs) {
        this.outputs = outputs;
        return this;
    }

    public String name() {
        return this.name;
    }

    public Type type() {
        return this.type;
    }

    public enum Type {
        MACHINE, SPLITTER, MERGER, PRODUCER, SINK
    }
}
