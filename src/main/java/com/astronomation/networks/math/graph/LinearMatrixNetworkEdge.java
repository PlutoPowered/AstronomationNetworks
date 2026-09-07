package com.astronomation.networks.math.graph;

import com.astronomation.networks.math.BigRational;
import com.astronomation.networks.model.Identifier;

public class LinearMatrixNetworkEdge {
    private LinearMatrixNetworkNode link;
    private Identifier item;
    private BigRational throughputMax;
    private BigRational quantity;

    public LinearMatrixNetworkEdge(LinearMatrixNetworkNode link, Identifier item, BigRational throughputMax, BigRational quantity) {
        this.link = link;
        this.item = item;
        this.throughputMax = throughputMax;
        this.quantity = quantity;
    }

    public LinearMatrixNetworkNode link() {
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
}
