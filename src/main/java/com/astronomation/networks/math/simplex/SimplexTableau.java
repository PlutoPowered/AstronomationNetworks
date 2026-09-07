package com.astronomation.networks.math.simplex;

import com.astronomation.networks.math.BigRational;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SimplexTableau {
    private SimplexMatrix matrix;

    private Map<String, Integer> variables;
    private Map<Integer, String> varNames;
    private Set<Integer> artificial;
    private SimplexConstraint objective;
    private List<SimplexConstraint> constraints;
    private boolean inverted;

    public SimplexTableau(SimplexMatrix matrix, Map<String, Integer> variables, Set<Integer> artificial, SimplexConstraint objective, List<SimplexConstraint> constraints, boolean inverted) {
        this.matrix = matrix;
        this.variables = variables;
        this.artificial = artificial;
        this.objective = objective;
        this.constraints = constraints;
        this.inverted = inverted;

        this.varNames = new HashMap<>();
        variables.forEach((str, ind) -> varNames.put(ind, str));
    }

    public static SimplexBuilder builder() {
        return new SimplexBuilder();
    }

    public SimplexTableau solve() {
        this.matrix.solve();
        return this;
    }

    public SimplexTableau solve(int maxIters) {
        this.matrix.solve(maxIters);
        return this;
    }

    public SimplexMatrix matrix() {
        return this.matrix;
    }

    public Set<String> variables() {
        return Collections.unmodifiableSet(this.variables.keySet());
    }

    public boolean solutionFound() {
        return this.artificial.stream().allMatch(i -> this.value(i).equals(BigRational.ZERO));
    }

    public boolean validate() {
        boolean valid = true;
        for (SimplexConstraint sc : this.constraints) {
            valid &= sc.validate(this);
        }
        return valid;
    }

    public boolean solved() {
        return this.solutionFound() && validate();
    }

    public boolean hasName(int index) {
        return this.varNames.containsKey(index);
    }

    public String name(int index) {
        if (!this.varNames.containsKey(index)) throw new SimplexException("Unknown or unnamed variable: " + index);
        return this.varNames.get(index);
    }

    public boolean hasValue(String var) {
        return this.variables.containsKey(var);
    }

    public BigRational value(String var) {
        if (!this.variables.containsKey(var)) throw new SimplexException("Unknown variable " + var);
        return this.matrix.variable(this.variables.get(var));
    }

    public BigRational value(int index) {
        return this.matrix.variable(index);
    }

    public BigRational output() {
        return this.inverted ? this.matrix.output().negate() : this.matrix.output();
    }

    public String solutionReport() {
        StringBuilder sb = new StringBuilder().append("Problem:\n").append("-".repeat(20)).append("\n");
        if (this.inverted) {
            sb.append("Minimize ");
        } else {
            sb.append("Maximize ");
        }

        sb.append(this.objective.toString(this)).append(", subject to:\n");
        this.constraints.forEach(sc -> sb.append(sc.toString(this)).append("\n"));

        sb.append("\nSolution:\n").append("-".repeat(20)).append("\n");
        sb.append("Solved: ").append(this.solved()).append("\n");
        sb.append("Output: ").append(this.output()).append("\n");
        sb.append("Variables:\n");
        this.variables().forEach(var -> sb.append(var).append(": ").append(hasValue(var) ? value(var) : "unknown").append("\n"));
        sb.append("\nValidation:\n").append("-".repeat(20)).append("\n");
        this.constraints.forEach(sc -> sb.append(sc.toString(this)).append(" --> ").append(sc.validate(this)).append("\n"));

        return sb.toString();
    }
}
