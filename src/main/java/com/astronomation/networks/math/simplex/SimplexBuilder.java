package com.astronomation.networks.math.simplex;

import com.astronomation.networks.math.BigRational;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SimplexBuilder {
    private Map<String, Integer> varMap = new HashMap<>();
    private SimplexConstraint objective;
    private List<SimplexConstraint> constraints = new ArrayList<>();
    private int nextIndex = 0;
    private boolean inverted = false;

    private List<Row> rows = new ArrayList<>();

    public class RowBuilder {
        private Map<Integer, Variable> variables = new HashMap<>();

        public RowBuilder var(BigRational coef, int index, VarType varType) {
            this.variables.put(index, new Variable(coef, index, varType));
            return this;
        }

        public RowBuilder slack(boolean positive) {
            return this.var(positive ? BigRational.ONE : BigRational.ONE.negate(), nextIndex(), VarType.SLACK);
        }

        public RowBuilder artificial() {
            return this.var(BigRational.ONE, nextIndex(), VarType.ARTIFICIAL);
        }

        public RowBuilder var(BigRational coef) {
            return this.var(coef, nextIndex(), VarType.EXPLICIT);
        }

        public RowBuilder var(BigRational coef, String var) {
            return this.var(coef, indexFor(var), VarType.EXPLICIT);
        }

        public RowBuilder var(long coef, String var) {
            return this.var(BigRational.of(coef), var);
        }

        public RowBuilder var(String coef, String var) {
            return this.var(BigRational.of(coef), var);
        }

        public RowBuilder var(long num, long denom, String var) {
            return this.var(BigRational.of(num, denom), var);
        }

        public SimplexBuilder equalTo(BigRational constant) {
            saveConstraint(SimplexConstraint.Op.EQ, constant);
            return this.artificial().build(constant);
        }

        public SimplexBuilder equalTo(long constant) {
            return this.equalTo(BigRational.of(constant));
        }

        public SimplexBuilder lessThanOrEqualTo(BigRational constant) {
            saveConstraint(SimplexConstraint.Op.LT_EQ, constant);
            if (constant.compareTo(BigRational.ZERO) < 0) {
                this.variables = negate(this.variables);
                return this.slack(false).artificial().build(constant);
            } else {
                return this.slack(true).build(constant);
            }
        }

        public SimplexBuilder lessThanOrEqualTo(long constant) {
            return this.lessThanOrEqualTo(BigRational.of(constant));
        }

        public SimplexBuilder greaterThanOrEqualTo(BigRational constant) {
            saveConstraint(SimplexConstraint.Op.GT_EQ, constant);
            if (constant.compareTo(BigRational.ZERO) <= 0) {
                this.variables = negate(this.variables);
                return this.slack(true).build(constant);
            } else {
                return this.slack(false).artificial().build(constant);
            }
        }

        public SimplexBuilder greaterThanOrEqualTo(long constant) {
            return this.greaterThanOrEqualTo(BigRational.of(constant));
        }

        public SimplexBuilder maximize() {
            objective = asConstraint(null, null);
            return this.build(null);
        }

        public SimplexBuilder minimize() {
            objective = asConstraint(null, null);
            this.variables = negate(this.variables);
            inverted = true;
            return this.build(null);
        }

        public SimplexBuilder build(BigRational constant) {
            return row(new Row(this.variables, constant));
        }

        private void saveConstraint(SimplexConstraint.Op op, BigRational constant) {
            constraints.add(asConstraint(op, constant));
        }

        private SimplexConstraint asConstraint(SimplexConstraint.Op op, BigRational constant) {
            int[] vars = new int[this.variables.size()];
            BigRational[] coefs = new BigRational[this.variables.size()];

            int varInd = 0;
            for (Variable var : this.variables.values()) {
                vars[varInd] = var.index();
                coefs[varInd] = var.coef();
                varInd++;
            }

            return new SimplexConstraint(vars, coefs, op, constant);
        }

        private static Map<Integer, Variable> negate(Map<Integer, Variable> vars) {
            return vars.values().stream().map(v -> new Variable(v.coef().negate(), v.index(), v.type()))
                    .collect(Collectors.toMap(Variable::index, Function.identity()));
        }

    }

    public RowBuilder row() {
        return new RowBuilder();
    }

    public SimplexBuilder row(Row row) {
        this.rows.add(row);
        return this;
    }

    public SimplexTableau build() {
        Row objectiveFunction = null;
        List<Row> rows = new ArrayList<>();

        for (Row row : this.rows) {
            if (row.constant() == null) {
                if (objectiveFunction == null) {
                    objectiveFunction = row;
                } else {
                    throw new SimplexException("Only one objective function is allowed");
                }
            } else {
                rows.add(row);
            }
        }

        if (objectiveFunction == null) throw new SimplexException("Objective function required but not found");
        if (objectiveFunction.variables().isEmpty()) throw new SimplexException("Objective function must have at least one variable");

        BigRational bigM = BigRational.ONE;

        for (Variable var : objectiveFunction.variables().values()) {
            bigM = BigRational.max(bigM, var.coef());
        }

        bigM = bigM.mul(BigRational.of(1_000_000)).reduce().negate();

        Map<Integer, Variable> artificialVariables = new HashMap<>();

        for (Row row : rows) {
            for (Variable var : row.variables().values()) {
                if (var.type() == VarType.ARTIFICIAL) artificialVariables.put(var.index(), var);
            }
        }

        int rowCount = rows.size();
        int colCount = this.nextIndex;

        int[] basis = new int[rowCount];
        BigRational[] basisCoeffs = new BigRational[rowCount];

        BigRational[] maxCoeffs = new BigRational[colCount];
        List<Map<Integer, BigRational>> columns = new ArrayList<>(colCount);
        for (int i = 0; i < colCount; i++) {
            columns.add(new HashMap<>());
        }
        BigRational[] constantColumn = new BigRational[rowCount];

        for (int i = 0; i < colCount; i++) {
            if (objectiveFunction.variables().containsKey(i)) {
                maxCoeffs[i] = objectiveFunction.variables().get(i).coef();
            } else if (artificialVariables.containsKey(i)) {
                maxCoeffs[i] = bigM;
            } else {
                maxCoeffs[i] = BigRational.ZERO;
            }
        }

        for (int row = 0; row < rowCount; row++) {
            Row curr = rows.get(row);
            constantColumn[row] = curr.constant();
            for (Variable var : curr.variables().values()) {
                if (var.coef().signum() != 0) {
                    columns.get(var.index()).put(row, var.coef());
                }
            }
        }

        int currBasis = 0;

        for (int col = 0; col < colCount; col++) {
            Map<Integer, BigRational> column = columns.get(col);
            boolean unit = column.size() == 1 && column.values().iterator().next().equals(BigRational.ONE);

            if (unit) {
                basis[currBasis] = col;
                basisCoeffs[currBasis] = maxCoeffs[col];
                currBasis++;
            }

            if (currBasis >= basis.length) {
                break;
            }
        }

        return new SimplexTableau(new SimplexMatrix(basis, basisCoeffs, maxCoeffs, columns, constantColumn), new HashMap<>(this.varMap), new HashSet<>(artificialVariables.keySet()), this.objective, new ArrayList<>(this.constraints), inverted);
    }

    private int indexFor(String name) {
        return this.varMap.computeIfAbsent(name, k -> nextIndex());
    }

    private int nextIndex() {
        int n = this.nextIndex;
        this.nextIndex++;
        return n;
    }

    public record Row(Map<Integer, Variable> variables, BigRational constant) { }

    public record Variable(BigRational coef, int index, VarType type) { }

    public enum VarType {EXPLICIT, SLACK, ARTIFICIAL}

}
