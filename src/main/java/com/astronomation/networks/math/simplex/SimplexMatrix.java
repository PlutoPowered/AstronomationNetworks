package com.astronomation.networks.math.simplex;

import com.astronomation.networks.math.BigRational;

public class SimplexMatrix {
    private int[] basis;
    private BigRational[] basisCoefficients;

    private BigRational[] maxCoefficients;
    private BigRational[][] coefficients; //[col][row]
    private int cols;
    private int rows;

    private BigRational[] constantColumn;

    public SimplexMatrix(int[] basis, BigRational[] basisCoefficients, BigRational[] maxCoefficients, BigRational[][] coefficients, BigRational[] constantColumn) {
        this.basis = basis;
        this.basisCoefficients = basisCoefficients;
        this.maxCoefficients = maxCoefficients;
        this.coefficients = coefficients;
        this.constantColumn = constantColumn;

        this.cols = coefficients.length;
        if (this.cols > 1) {
            this.rows = coefficients[0].length;
        }
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
        BigRational maxPivot = null;
        int keyCol = -1;

        //Find the pivot col
        for (int col = 0; col < this.coefficients.length; col++) {
            BigRational[] rowArr = this.coefficients[col];
            BigRational z_j = BigRational.ZERO;
            for (int row = 0; row < rowArr.length; row++) {
                z_j = z_j.add(this.basisCoefficients[row].mul(rowArr[row]));
            }

            BigRational benefit = this.maxCoefficients[col].sub(z_j);
            if (maxPivot == null || maxPivot.compareTo(benefit) < 0) {
                maxPivot = benefit;
                keyCol = col;
            }
        }

        //If the max benefit is 0 or less, terminate
        if (maxPivot == null || maxPivot.compareTo(BigRational.ZERO) <= 0) {
            return StepResult.TERMINATE;
        }

        //Find the pivot row
        BigRational minPivot = null;
        int minVar = 0;
        int keyRow = -1;
        for (int row = 0; row < this.constantColumn.length; row++) {
            if (this.coefficients[keyCol][row].compareTo(BigRational.ZERO) > 0) {
                BigRational ratio = this.constantColumn[row].div(this.coefficients[keyCol][row]);
                int cmp = minPivot == null ? 0 : minPivot.compareTo(ratio);

                if (minPivot == null || cmp > 0 || (cmp == 0 && this.basis[row] < minVar)) {
                    minPivot = ratio;
                    keyRow = row;
                    minVar = this.basis[row];
                }
            }
        }

        if (minPivot == null) return StepResult.TERMINATE;

        //Replace the old basis with the new pivot basis
        BigRational pivot = this.coefficients[keyCol][keyRow];
        this.basis[keyRow] = keyCol;
        this.basisCoefficients[keyRow] = this.maxCoefficients[keyCol];

        //Divide each element in the pivot row by the pivot element
        for (int col = 0; col < this.coefficients.length; col++) {
            this.coefficients[col][keyRow] = this.coefficients[col][keyRow].div(pivot).reduce();
        }
        this.constantColumn[keyRow] = this.constantColumn[keyRow].div(pivot).reduce();

        //Adjust coefficients to be relative to the pivot row
        for (int row = 0; row < this.rows; row++) {
            BigRational factor = this.coefficients[keyCol][row];
            if (row != keyRow) {
                for (int col = 0; col < this.cols; col++) {
                    this.coefficients[col][row] = this.coefficients[col][row].sub(factor.mul(this.coefficients[col][keyRow])).reduce();
                }

                this.constantColumn[row] = this.constantColumn[row].sub(factor.mul(this.constantColumn[keyRow])).reduce();
            }
        }

        return StepResult.CONTINUE;
    }

    public BigRational variable(int index) {
        for (int i = 0; i < this.basis.length; i++) {
            if (this.basis[i] == index) return this.constantColumn[i];
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
