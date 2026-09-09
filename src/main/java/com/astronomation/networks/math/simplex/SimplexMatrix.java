package com.astronomation.networks.math.simplex;

import com.astronomation.networks.math.BigRational;

import java.util.List;
import java.util.Map;

public class SimplexMatrix {
    private int[] basis;
    private BigRational[] basisCoefficients;

    private BigRational[] maxCoefficients;
    private List<Map<Integer, BigRational>> columns; //[col] -> sparse row->coefficient, the original (never mutated) constraint matrix
    private BigRational[][] basisInverse; //[row][row], the current basis inverse
    private BigRational[] price; //c_B^T * basisInverse, maintained incrementally rather than recomputed every step

    private int cols;
    private int rows;

    private BigRational[] constantColumn;

    public SimplexMatrix(int[] basis, BigRational[] basisCoefficients, BigRational[] maxCoefficients, List<Map<Integer, BigRational>> columns, BigRational[] constantColumn) {
        this.basis = basis;
        this.basisCoefficients = basisCoefficients;
        this.maxCoefficients = maxCoefficients;
        this.columns = columns;
        this.constantColumn = constantColumn;

        this.cols = columns.size();
        this.rows = constantColumn.length;

        this.basisInverse = new BigRational[this.rows][this.rows];
        for (int row = 0; row < this.rows; row++) {
            for (int col = 0; col < this.rows; col++) {
                this.basisInverse[row][col] = row == col ? BigRational.ONE : BigRational.ZERO;
            }
        }

        this.price = basisCoefficients.clone();
    }

    public void solve(int maxIters) {
        StepResult result;
        int iter = 0;
        do {
            result = step();
            iter++;
        } while (result != StepResult.TERMINATE && iter <= maxIters);
    }

    public void solve() {
        StepResult result;
        do {
            result = step();
        } while (result != StepResult.TERMINATE);
    }

    public StepResult step() {
        //Find the pivot col: price reduced costs off the sparse original columns instead of a dense per-row scan
        BigRational maxPivot = null;
        int keyCol = -1;
        for (int col = 0; col < this.cols; col++) {
            BigRational benefit = this.maxCoefficients[col].sub(dot(this.price, this.columns.get(col)));
            if (maxPivot == null || maxPivot.compareTo(benefit) < 0) {
                maxPivot = benefit;
                keyCol = col;
            }
        }

        //If the max benefit is 0 or less, terminate
        if (maxPivot == null || maxPivot.compareTo(BigRational.ZERO) <= 0) {
            return StepResult.TERMINATE;
        }

        //Expand the chosen column into current-basis coordinates via B^-1 * A_j, needed for both the ratio test and the pivot
        BigRational[] enteringColumn = expand(this.columns.get(keyCol));

        //Find the pivot row
        BigRational minPivot = null;
        int minVar = 0;
        int keyRow = -1;
        for (int row = 0; row < this.rows; row++) {
            if (enteringColumn[row].compareTo(BigRational.ZERO) > 0) {
                BigRational ratio = this.constantColumn[row].div(enteringColumn[row]);
                int cmp = minPivot == null ? 0 : minPivot.compareTo(ratio);

                if (minPivot == null || cmp > 0 || (cmp == 0 && this.basis[row] < minVar)) {
                    minPivot = ratio;
                    keyRow = row;
                    minVar = this.basis[row];
                }
            }
        }

        if (minPivot == null) {
            return StepResult.TERMINATE;
        }

        //Replace the old basis with the new pivot basis
        BigRational pivot = enteringColumn[keyRow];
        this.basis[keyRow] = keyCol;
        this.basisCoefficients[keyRow] = this.maxCoefficients[keyCol];

        //Divide the pivot row of the basis inverse by the pivot element, and update price incrementally off of it:
        //price_new = price_old + benefit * (updated pivot row of basisInverse) — avoids recomputing c_B^T * basisInverse from scratch
        for (int col = 0; col < this.rows; col++) {
            BigRational scaled = this.basisInverse[keyRow][col].div(pivot).reduce();
            this.basisInverse[keyRow][col] = scaled;
            this.price[col] = this.price[col].add(maxPivot.mul(scaled)).reduce();
        }
        this.constantColumn[keyRow] = this.constantColumn[keyRow].div(pivot).reduce();

        //Adjust every other row of the basis inverse relative to the pivot row; the dense per-column sweep only ever
        //touches the rows x rows inverse now, never the (much larger) column count
        for (int row = 0; row < this.rows; row++) {
            if (row == keyRow) {
                continue;
            }

            BigRational factor = enteringColumn[row];
            if (factor.signum() == 0) {
                continue;
            }

            for (int col = 0; col < this.rows; col++) {
                this.basisInverse[row][col] = this.basisInverse[row][col].sub(factor.mul(this.basisInverse[keyRow][col])).reduce();
            }
            this.constantColumn[row] = this.constantColumn[row].sub(factor.mul(this.constantColumn[keyRow])).reduce();
        }

        return StepResult.CONTINUE;
    }

    private static BigRational dot(BigRational[] price, Map<Integer, BigRational> sparseColumn) {
        BigRational sum = BigRational.ZERO;
        for (Map.Entry<Integer, BigRational> entry : sparseColumn.entrySet()) {
            sum = sum.add(price[entry.getKey()].mul(entry.getValue()));
        }
        return sum.reduce();
    }

    private BigRational[] expand(Map<Integer, BigRational> sparseColumn) {
        BigRational[] result = new BigRational[this.rows];
        for (int row = 0; row < this.rows; row++) {
            result[row] = BigRational.ZERO;
        }

        for (Map.Entry<Integer, BigRational> entry : sparseColumn.entrySet()) {
            int structuralRow = entry.getKey();
            BigRational coefficient = entry.getValue();
            for (int row = 0; row < this.rows; row++) {
                result[row] = result[row].add(this.basisInverse[row][structuralRow].mul(coefficient)).reduce();
            }
        }

        return result;
    }

    public BigRational variable(int index) {
        for (int i = 0; i < this.basis.length; i++) {
            if (this.basis[i] == index) {
                return this.constantColumn[i];
            }
        }

        return BigRational.ZERO;
    }

    public BigRational output() {
        BigRational out = BigRational.ZERO;
        for (int row = 0; row < this.constantColumn.length; row++) {
            out = out.add(this.constantColumn[row].mul(this.basisCoefficients[row]));
        }

        return out.reduce();
    }

    public enum StepResult {
        TERMINATE, CONTINUE;
    }

}
