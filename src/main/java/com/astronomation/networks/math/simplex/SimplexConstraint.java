package com.astronomation.networks.math.simplex;

import com.astronomation.networks.math.BigRational;

public record SimplexConstraint(int[] vars, BigRational[] coeffs, Op operator, BigRational constant) {

    enum Op {
        EQ("="), LT_EQ("<="), GT_EQ(">=");

        private String repr;

        Op(String repr) {
            this.repr = repr;
        }

        public String repr() {
            return this.repr;
        }
    }

    public boolean validate(SimplexTableau tableau) {
        BigRational lhs = BigRational.ZERO;
        for (int i = 0; i < this.vars.length; i++) {
            lhs = lhs.add(tableau.value(this.vars[i]).mul(this.coeffs[i])).reduce();
        }

        BigRational cmp = lhs;
        return switch (this.operator) {
            case EQ -> cmp.compareTo(this.constant) == 0;
            case LT_EQ -> cmp.compareTo(this.constant) <= 0;
            case GT_EQ -> cmp.compareTo(this.constant) >= 0;
        };
    }

    public String toString(SimplexTableau tableau) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < this.vars.length; i++) {
            if (i == 0) {
                sb.append(this.coeffs[i].reduce()).append(getName(tableau, this.vars[i]));
            } else if (this.coeffs[i].compareTo(BigRational.ZERO) >= 0) {
                sb.append(" + ").append(this.coeffs[i].reduce()).append(getName(tableau, this.vars[i]));
            } else {
                sb.append(" - ").append(this.coeffs[i].negate().reduce()).append(getName(tableau, this.vars[i]));
            }
        }

        if (this.operator != null && this.constant != null) {
            return sb.append(" ").append(this.operator.repr()).append(" ").append(this.constant.reduce()).toString();
        } else {
            return sb.toString();
        }
    }

    private String getName(SimplexTableau tableau, int var) {
        return tableau.hasName(var) ? tableau.name(var) : "S_" + var;
    }

}
