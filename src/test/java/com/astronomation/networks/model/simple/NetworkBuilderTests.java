package com.astronomation.networks.model.simple;

import com.astronomation.networks.math.BigRational;
import com.astronomation.networks.math.graph.LinearMatrixBuilder;
import com.astronomation.networks.math.graph.LinearMatrixNetworkEdge;
import com.astronomation.networks.math.graph.LinearMatrixNetworkNode;
import com.astronomation.networks.math.simplex.SimplexTableau;
import com.astronomation.networks.model.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class NetworkBuilderTests {

    // -------------------------------------------------------------------------
    // Test 1: 2 nodes — PRODUCER → SINK (simplest possible network)
    //
    //   P(cap=10) ──→ SINK
    //
    // Expected: objective = 10, single edge saturates its capacity.
    // -------------------------------------------------------------------------
    @Test
    public void test_linear_2node() {
        LinearMatrixNetworkNode p    = new LinearMatrixNetworkNode(new Identifier.String("P"),    LinearMatrixNetworkNode.Type.PRODUCER);
        LinearMatrixNetworkNode sink = new LinearMatrixNetworkNode(new Identifier.String("SINK"), LinearMatrixNetworkNode.Type.SINK);

        LinearMatrixNetworkEdge p_out  = new LinearMatrixNetworkEdge(sink, new Identifier.String("item"), BigRational.of(10), null);
        LinearMatrixNetworkEdge s_in   = new LinearMatrixNetworkEdge(p,    new Identifier.String("item"), null, null);

        p.inputs(List.of()).outputs(List.of(p_out));
        sink.inputs(List.of(s_in)).outputs(List.of());

        SimplexTableau tableau = new LinearMatrixBuilder()
                .discoverNodes(p)
                .build().solve();

        assertTrue(tableau.solved());
        assertEquals(BigRational.of(10), tableau.output());
        assertEquals(BigRational.of(10), tableau.value("e_P_SINK"));
    }

    // -------------------------------------------------------------------------
    // Test 2: 3 nodes — PRODUCER → MACHINE(2:1) → SINK (tree with recipe ratio)
    //
    //   P(cap=20) ──[ore, qty=2]──→ M ──[plate, qty=1]──→ SINK
    //
    // M runs at rate r_M. Constraints: e_P_M = 2*r_M, e_M_SINK = r_M.
    // Capacity forces e_P_M ≤ 20, so r_M = 10.
    // Expected: objective = 20, e_P_M = 20, e_M_SINK = 10, r_M = 10.
    // -------------------------------------------------------------------------
    @Test
    public void test_machine_ratio_3node() {
        LinearMatrixNetworkNode p    = new LinearMatrixNetworkNode(new Identifier.String("P"),    LinearMatrixNetworkNode.Type.PRODUCER);
        LinearMatrixNetworkNode m    = new LinearMatrixNetworkNode(new Identifier.String("M"),    LinearMatrixNetworkNode.Type.MACHINE);
        LinearMatrixNetworkNode sink = new LinearMatrixNetworkNode(new Identifier.String("SINK"), LinearMatrixNetworkNode.Type.SINK);

        LinearMatrixNetworkEdge p_out  = new LinearMatrixNetworkEdge(m,    new Identifier.String("ore"),   BigRational.of(20), null);
        LinearMatrixNetworkEdge m_in   = new LinearMatrixNetworkEdge(p,    new Identifier.String("ore"),   null, BigRational.of(2));
        LinearMatrixNetworkEdge m_out  = new LinearMatrixNetworkEdge(sink, new Identifier.String("plate"), null, BigRational.of(1));
        LinearMatrixNetworkEdge s_in   = new LinearMatrixNetworkEdge(m,    new Identifier.String("plate"), null, null);

        p.inputs(List.of()).outputs(List.of(p_out));
        m.inputs(List.of(m_in)).outputs(List.of(m_out));
        sink.inputs(List.of(s_in)).outputs(List.of());

        SimplexTableau tableau = new LinearMatrixBuilder()
                .discoverNodes(p)
                .build().solve();

        assertTrue(tableau.solved());
        assertEquals(BigRational.of(20), tableau.output());
        assertEquals(BigRational.of(20), tableau.value("e_P_M"));
        assertEquals(BigRational.of(10), tableau.value("e_M_SINK"));
        assertEquals(BigRational.of(10), tableau.value("r_M"));
    }

    // -------------------------------------------------------------------------
    // Test 3: 4 nodes — P → SPLITTER → SINK1 + SINK2 (pure tree, two outputs)
    //
    //               ┌──→ SINK1
    //   P(cap=12) ──→ SPL
    //               └──→ SINK2
    //
    // SPL conservation: e_P_SPL = e_SPL_SINK1 + e_SPL_SINK2.
    // Expected: objective = 12, individual split is underdetermined but sums to 12.
    // -------------------------------------------------------------------------
    @Test
    public void test_splitter_tree_4node() {
        LinearMatrixNetworkNode p     = new LinearMatrixNetworkNode(new Identifier.String("P"),     LinearMatrixNetworkNode.Type.PRODUCER);
        LinearMatrixNetworkNode spl   = new LinearMatrixNetworkNode(new Identifier.String("SPL"),   LinearMatrixNetworkNode.Type.SPLITTER);
        LinearMatrixNetworkNode sink1 = new LinearMatrixNetworkNode(new Identifier.String("SINK1"), LinearMatrixNetworkNode.Type.SINK);
        LinearMatrixNetworkNode sink2 = new LinearMatrixNetworkNode(new Identifier.String("SINK2"), LinearMatrixNetworkNode.Type.SINK);

        LinearMatrixNetworkEdge p_out    = new LinearMatrixNetworkEdge(spl,   new Identifier.String("item"), BigRational.of(12), null);
        LinearMatrixNetworkEdge spl_in   = new LinearMatrixNetworkEdge(p,     new Identifier.String("item"), null, null);
        LinearMatrixNetworkEdge spl_out1 = new LinearMatrixNetworkEdge(sink1, new Identifier.String("item"), null, null);
        LinearMatrixNetworkEdge spl_out2 = new LinearMatrixNetworkEdge(sink2, new Identifier.String("item"), null, null);
        LinearMatrixNetworkEdge s1_in    = new LinearMatrixNetworkEdge(spl,   new Identifier.String("item"), null, null);
        LinearMatrixNetworkEdge s2_in    = new LinearMatrixNetworkEdge(spl,   new Identifier.String("item"), null, null);

        p.inputs(List.of()).outputs(List.of(p_out));
        spl.inputs(List.of(spl_in)).outputs(List.of(spl_out1, spl_out2));
        sink1.inputs(List.of(s1_in)).outputs(List.of());
        sink2.inputs(List.of(s2_in)).outputs(List.of());

        SimplexTableau tableau = new LinearMatrixBuilder()
                .discoverNodes(p)
                .build().solve();

        assertTrue(tableau.solved());
        assertEquals(BigRational.of(12), tableau.output());
        assertEquals(BigRational.of(12), tableau.value("e_P_SPL"));

        // Split ratio is underdetermined; only the total is guaranteed
        BigRational toSink1 = tableau.value("e_SPL_SINK1");
        BigRational toSink2 = tableau.value("e_SPL_SINK2");
        assertEquals(BigRational.of(12), toSink1.add(toSink2));
    }

    // -------------------------------------------------------------------------
    // Test 4: 4 nodes — P1 + P2 → MERGER → SINK (pure tree, two inputs)
    //
    //   P1(cap=5) ──┐
    //               ├──→ MER ──→ SINK
    //   P2(cap=8) ──┘
    //
    // MER conservation: e_P1_MER + e_P2_MER = e_MER_SINK.
    // Expected: objective = 13 (both producers at full capacity).
    // -------------------------------------------------------------------------
    @Test
    public void test_merger_tree_4node() {
        LinearMatrixNetworkNode p1   = new LinearMatrixNetworkNode(new Identifier.String("P1"),   LinearMatrixNetworkNode.Type.PRODUCER);
        LinearMatrixNetworkNode p2   = new LinearMatrixNetworkNode(new Identifier.String("P2"),   LinearMatrixNetworkNode.Type.PRODUCER);
        LinearMatrixNetworkNode mer  = new LinearMatrixNetworkNode(new Identifier.String("MER"),  LinearMatrixNetworkNode.Type.MERGER);
        LinearMatrixNetworkNode sink = new LinearMatrixNetworkNode(new Identifier.String("SINK"), LinearMatrixNetworkNode.Type.SINK);

        LinearMatrixNetworkEdge p1_out  = new LinearMatrixNetworkEdge(mer,  new Identifier.String("iron"),  BigRational.of(5), null);
        LinearMatrixNetworkEdge p2_out  = new LinearMatrixNetworkEdge(mer,  new Identifier.String("coal"),  BigRational.of(8), null);
        LinearMatrixNetworkEdge mer_in1 = new LinearMatrixNetworkEdge(p1,   new Identifier.String("iron"),  null, null);
        LinearMatrixNetworkEdge mer_in2 = new LinearMatrixNetworkEdge(p2,   new Identifier.String("coal"),  null, null);
        LinearMatrixNetworkEdge mer_out = new LinearMatrixNetworkEdge(sink, new Identifier.String("mixed"), null, null);
        LinearMatrixNetworkEdge s_in    = new LinearMatrixNetworkEdge(mer,  new Identifier.String("mixed"), null, null);

        p1.inputs(List.of()).outputs(List.of(p1_out));
        p2.inputs(List.of()).outputs(List.of(p2_out));
        mer.inputs(List.of(mer_in1, mer_in2)).outputs(List.of(mer_out));
        sink.inputs(List.of(s_in)).outputs(List.of());

        SimplexTableau tableau = new LinearMatrixBuilder()
                .discoverNodes(p1)
                .build().solve();

        assertTrue(tableau.solved());
        assertEquals(BigRational.of(13), tableau.output());
        assertEquals(BigRational.of(5),  tableau.value("e_P1_MER"));
        assertEquals(BigRational.of(8),  tableau.value("e_P2_MER"));
        assertEquals(BigRational.of(13), tableau.value("e_MER_SINK"));
    }

    // -------------------------------------------------------------------------
    // Test 5: 5 nodes — P1 + P2 → MACHINE(1,1:2) → SINK (cross-branch)
    //
    //   P1(cap=10) ──[ore,  qty=1]──┐
    //                               ├──→ M ──[plate, qty=2]──→ SINK
    //   P2(cap=6)  ──[coal, qty=1]──┘
    //
    // M needs 1 ore AND 1 coal per cycle (rate r_M), produces 2 plates.
    // P2 is the bottleneck: r_M = 6. P1 is only used at 6/10 capacity.
    // Expected: objective = 12 (e_P1_M + e_P2_M = 6 + 6), r_M = 6, e_M_SINK = 12.
    // -------------------------------------------------------------------------
    @Test
    public void test_cross_branch_5node() {
        LinearMatrixNetworkNode p1   = new LinearMatrixNetworkNode(new Identifier.String("P1"),   LinearMatrixNetworkNode.Type.PRODUCER);
        LinearMatrixNetworkNode p2   = new LinearMatrixNetworkNode(new Identifier.String("P2"),   LinearMatrixNetworkNode.Type.PRODUCER);
        LinearMatrixNetworkNode m    = new LinearMatrixNetworkNode(new Identifier.String("M"),    LinearMatrixNetworkNode.Type.MACHINE);
        LinearMatrixNetworkNode sink = new LinearMatrixNetworkNode(new Identifier.String("SINK"), LinearMatrixNetworkNode.Type.SINK);

        LinearMatrixNetworkEdge p1_out = new LinearMatrixNetworkEdge(m,    new Identifier.String("ore"),   BigRational.of(10), null);
        LinearMatrixNetworkEdge p2_out = new LinearMatrixNetworkEdge(m,    new Identifier.String("coal"),  BigRational.of(6),  null);
        LinearMatrixNetworkEdge m_in1  = new LinearMatrixNetworkEdge(p1,   new Identifier.String("ore"),   null, BigRational.of(1));
        LinearMatrixNetworkEdge m_in2  = new LinearMatrixNetworkEdge(p2,   new Identifier.String("coal"),  null, BigRational.of(1));
        LinearMatrixNetworkEdge m_out  = new LinearMatrixNetworkEdge(sink, new Identifier.String("plate"), null, BigRational.of(2));
        LinearMatrixNetworkEdge s_in   = new LinearMatrixNetworkEdge(m,    new Identifier.String("plate"), null, null);

        p1.inputs(List.of()).outputs(List.of(p1_out));
        p2.inputs(List.of()).outputs(List.of(p2_out));
        m.inputs(List.of(m_in1, m_in2)).outputs(List.of(m_out));
        sink.inputs(List.of(s_in)).outputs(List.of());

        SimplexTableau tableau = new LinearMatrixBuilder()
                .discoverNodes(p1)
                .build().solve();

        assertTrue(tableau.solved());
        assertEquals(BigRational.of(12), tableau.output());
        assertEquals(BigRational.of(6),  tableau.value("e_P1_M"));
        assertEquals(BigRational.of(6),  tableau.value("e_P2_M"));
        assertEquals(BigRational.of(6),  tableau.value("r_M"));
        assertEquals(BigRational.of(12), tableau.value("e_M_SINK"));
    }

    // -------------------------------------------------------------------------
    // Test 6: 4 nodes — loop back: P → MACHINE → SPLITTER → SINK
    //                                                  └──────→ MACHINE (cycle)
    //
    //   P(cap=10) ──→ M ──→ SPL ──→ SINK
    //                  ↑          │
    //                  └──(cap=5)─┘
    //
    // M has no quantities → simple conservation: e_P_M + e_SPL_M = e_M_SPL.
    // SPL conservation: e_M_SPL = e_SPL_SINK + e_SPL_M.
    // Combining: e_P_M = e_SPL_SINK (loop cancels out).
    // Expected: objective = 10, e_SPL_SINK = 10. Cycle doesn't affect the result.
    // -------------------------------------------------------------------------
    @Test
    public void test_loop_back_4node() {
        LinearMatrixNetworkNode p    = new LinearMatrixNetworkNode(new Identifier.String("P"),    LinearMatrixNetworkNode.Type.PRODUCER);
        LinearMatrixNetworkNode m    = new LinearMatrixNetworkNode(new Identifier.String("M"),    LinearMatrixNetworkNode.Type.MACHINE);
        LinearMatrixNetworkNode spl  = new LinearMatrixNetworkNode(new Identifier.String("SPL"),  LinearMatrixNetworkNode.Type.SPLITTER);
        LinearMatrixNetworkNode sink = new LinearMatrixNetworkNode(new Identifier.String("SINK"), LinearMatrixNetworkNode.Type.SINK);

        LinearMatrixNetworkEdge p_out        = new LinearMatrixNetworkEdge(m,    new Identifier.String("item"), BigRational.of(10), null);
        LinearMatrixNetworkEdge m_out        = new LinearMatrixNetworkEdge(spl,  new Identifier.String("item"), null, null);
        LinearMatrixNetworkEdge spl_to_sink  = new LinearMatrixNetworkEdge(sink, new Identifier.String("item"), null, null);
        LinearMatrixNetworkEdge spl_to_m     = new LinearMatrixNetworkEdge(m,    new Identifier.String("item"), BigRational.of(5),  null);  // loop back, cap=5

        LinearMatrixNetworkEdge m_in_p   = new LinearMatrixNetworkEdge(p,   new Identifier.String("item"), null, null);
        LinearMatrixNetworkEdge m_in_spl = new LinearMatrixNetworkEdge(spl, new Identifier.String("item"), null, null);
        LinearMatrixNetworkEdge spl_in   = new LinearMatrixNetworkEdge(m,   new Identifier.String("item"), null, null);
        LinearMatrixNetworkEdge sink_in  = new LinearMatrixNetworkEdge(spl, new Identifier.String("item"), null, null);

        p.inputs(List.of()).outputs(List.of(p_out));
        m.inputs(List.of(m_in_p, m_in_spl)).outputs(List.of(m_out));
        spl.inputs(List.of(spl_in)).outputs(List.of(spl_to_sink, spl_to_m));
        sink.inputs(List.of(sink_in)).outputs(List.of());

        SimplexTableau tableau = new LinearMatrixBuilder()
                .discoverNodes(p)
                .build().solve();

        assertTrue(tableau.solved());
        // The cycle cancels: e_P_M always equals e_SPL_SINK
        assertEquals(BigRational.of(10), tableau.output());
        assertEquals(BigRational.of(10), tableau.value("e_P_M"));
        assertEquals(BigRational.of(10), tableau.value("e_SPL_SINK"));
    }

}
