package com.astronomation.networks.model.plan;

import com.astronomation.networks.math.BigRational;
import com.astronomation.networks.model.Network;

import java.util.List;
import java.util.Set;

public interface TickPlan {

    List<Sentinel> preCycle();

    List<Sentinel> terminalCycle();

    Sentinel terminalAverage();

    interface Builder {

        Sentinel.Builder sentinel();

        Builder preCycleTick(Sentinel sentinel);

        Builder terminalCycleTick(Sentinel sentinel);

        Builder terminalAverage(Sentinel sentinel);

        TickPlan build();

    }

    interface Sentinel {

        Set<Delta> deltas(Network.Node node);

        interface Builder {

            Builder delta(Network.Node node, String item, BigRational quantity);

            Sentinel build();

        }

    }

    interface Delta {

        String item();

        BigRational quantity();

    }

}
