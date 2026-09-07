package com.astronomation.networks.model.plan;

import com.astronomation.networks.math.BigRational;
import com.astronomation.networks.model.Network;

import java.util.List;
import java.util.Set;

public interface TickPlan {

    List<Sentinel> preCycle();

    List<Sentinel> terminalCycle();

    Sentinel terminalAverage();

    interface Sentinel {

        Set<Delta> deltas(Network.Node node);

    }

    interface Delta {

        String item();

        BigRational quantity();

    }

}
