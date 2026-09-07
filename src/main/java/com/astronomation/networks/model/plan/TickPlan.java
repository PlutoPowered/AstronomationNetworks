package com.astronomation.networks.model.plan;

import com.astronomation.networks.math.BigRational;
import com.astronomation.networks.model.Identifier;
import com.astronomation.networks.model.Network;

import java.util.List;
import java.util.Set;

public interface TickPlan {

    List<Sentinel> preCycle();

    List<Sentinel> terminalCycle();

    Sentinel terminalAverage();

    interface Builder {

        Sentinel.Builder preCycleTick();

        Sentinel.Builder terminalCycleTick();

        Sentinel.Builder terminalAverage();

        TickPlan build();

    }

    interface Sentinel {

        Set<Delta> deltas(Network.Node node);

        interface Builder {

            Builder delta(Network.Node node, Identifier item, BigRational quantity);

            TickPlan.Builder build();

        }

    }

    interface Delta {

        Identifier item();

        BigRational quantity();

    }

}
