package com.astronomation.networks.math.graph;

import com.astronomation.networks.math.BigRational;

public class LinearMatrixNetworkEdge {
    private LinearMatrixNetworkNode link;
    private String item;
    private BigRational throughputMax;
    private BigRational quantity;

    public LinearMatrixNetworkEdge(LinearMatrixNetworkNode link, String item, BigRational throughputMax, BigRational quantity) {
        this.link = link;
        this.item = item;
        this.throughputMax = throughputMax;
        this.quantity = quantity;
    }

    public LinearMatrixNetworkNode link() {
        return this.link;
    }

    public String item() {
        return this.item;
    }

    public BigRational throughputMax() {
        return this.throughputMax;
    }

    public BigRational quantity() {
        return this.quantity;
    }
}
