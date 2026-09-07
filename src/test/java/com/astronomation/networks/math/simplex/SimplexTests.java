package com.astronomation.networks.math.simplex;

import com.astronomation.networks.math.BigRational;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SimplexTests {

    @Test
    public void testSimplex_solution_1() {
        SimplexTableau tableau = SimplexTableau.builder()
                .row().var(7, "x1").var(6, "x2").maximize()
                .row().var(2, "x1").var(4, "x2").lessThanOrEqualTo(16)
                .row().var(3, "x1").var(2, "x2").lessThanOrEqualTo(12)
                .build().solve();

        assertTrue(tableau.solved());
        assertEquals(BigRational.of(32), tableau.output());
        assertEquals(BigRational.of(2), tableau.value("x1"));
        assertEquals(BigRational.of(3), tableau.value("x2"));
    }

    @Test
    public void testSimplex_solution_2() {
        SimplexTableau tableau = SimplexTableau.builder()
                .row().var(-2, "x").var(-3, "y").var(-4, "z").minimize()
                .row().var(3, "x").var(2, "y").var(1, "z").lessThanOrEqualTo(10)
                .row().var(2, "x").var(5, "y").var(3, "z").lessThanOrEqualTo(15)
                .build().solve();

        assertTrue(tableau.solved());
        assertEquals(BigRational.of(-20), tableau.output());
        assertEquals(BigRational.of(0), tableau.value("x"));
        assertEquals(BigRational.of(0), tableau.value("y"));
        assertEquals(BigRational.of(5), tableau.value("z"));
    }

    @Test
    public void testSimplex_solution_3() {
        SimplexTableau tableau = SimplexTableau.builder()
                .row().var(-2, "x").var(-3, "y").var(-4, "z").minimize()
                .row().var(3, "x").var(2, "y").var(1, "z").equalTo(10)
                .row().var(2, "x").var(5, "y").var(3, "z").equalTo(15)
                .build().solve();

        assertTrue(tableau.solved());
        assertEquals(BigRational.of(-130, 7), tableau.output());
        assertEquals(BigRational.of(15, 7), tableau.value("x"));
        assertEquals(BigRational.of(0), tableau.value("y"));
        assertEquals(BigRational.of(25, 7), tableau.value("z"));
    }

    @Test
    public void testSimplex_solution_4() {
        SimplexTableau tableau = SimplexTableau.builder()
                .row().var(7, "x1").var(6, "x2").maximize()
                .row().var(2, "x1").var(4, "x2").lessThanOrEqualTo(16)
                .row().var(3, "x1").var(2, "x2").lessThanOrEqualTo(12)
                .row().var(1, "x1").lessThanOrEqualTo(100)
                .row().var(1, "x2").lessThanOrEqualTo(100)
                .build().solve();

        assertTrue(tableau.solved());
        assertEquals(BigRational.of(32), tableau.output());
        assertEquals(BigRational.of(2), tableau.value("x1"));
        assertEquals(BigRational.of(3), tableau.value("x2"));
    }

    @Test
    public void testSimplex_noSolution() {
        SimplexTableau tableau = SimplexTableau.builder()
                .row().var(1, "x").maximize()
                .row().var(1, "x").equalTo(1)
                .row().var(1, "x").equalTo(2)
                .build().solve();

        assertFalse(tableau.solved());
        System.out.println(tableau.solutionReport());
    }

}
