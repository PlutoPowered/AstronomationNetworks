package com.astronomation.networks.math;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Objects;

public final class BigRational implements Comparable<BigRational> {
    public static final BigRational ZERO = BigRational.of(0);
    public static final BigRational ONE = BigRational.of(1);
    public static final BigRational NEGATIVE_ONE = BigRational.of(-1);

    //Value fields
    private BigInteger numerator;
    private BigInteger denominator;

    //Transient fields
    private boolean neverReduced = true;
    private BigRational reduced = null;

    BigRational() {

    }

    private BigRational(BigInteger numerator, BigInteger denominator, boolean neverReduced) {
        if (denominator.signum() == -1) {
            //Positive denominator
            denominator = denominator.negate();
            numerator = numerator.negate();
        }

        this.numerator = numerator;
        this.denominator = denominator;
        this.neverReduced = neverReduced;
    }

    public BigRational(BigInteger numerator, BigInteger denominator) {
        this(numerator, denominator, true);
    }

    public static BigRational of(long val) {
        return new BigRational(BigInteger.valueOf(val), BigInteger.ONE, false);
    }

    public static BigRational of(long numerator, long denominator) {
        return new BigRational(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator), true);
    }

    public static BigRational ofDecimalStr(String decimal) {
        return of(new BigDecimal(decimal));
    }

    public static BigRational of(String rat) {
        String[] parts = rat.split("/", 2);
        return new BigRational(new BigInteger(parts[0].trim()),
                parts.length == 2 ? new BigInteger(parts[1].trim()) : BigInteger.ONE, true);
    }

    public static BigRational of(double decimal) {
        return of(new BigDecimal(decimal));
    }

    public static BigRational of(BigDecimal decimal) {
        int scale = decimal.scale();
        if (scale <= 0) {
            return new BigRational(decimal.toBigInteger(), BigInteger.ONE, false);
        } else {
            BigInteger denominator = BigInteger.TEN.pow(scale);
            BigInteger numerator = decimal.unscaledValue();
            BigInteger gcd = numerator.gcd(denominator);
            return new BigRational(numerator.divide(gcd), denominator.divide(gcd), false);
        }
    }

    public double doubleApprox() {
        return this.numerator().doubleValue() / this.denominator().doubleValue();
    }

    public BigDecimal approx(int scale, RoundingMode roundingMode) {
        return new BigDecimal(this.numerator)
                .divide(new BigDecimal(this.denominator), scale, roundingMode);
    }

    public int signum() {
        return this.numerator.signum();
    }

    public BigInteger numerator() {
        return this.numerator;
    }

    public BigInteger denominator() {
        return this.denominator;
    }

    public boolean neverReduced() {
        return this.neverReduced;
    }

    public BigRational reduce() {
        if (this.neverReduced) {
            if (this.reduced == null) {
                BigInteger gcd = this.numerator.gcd(this.denominator);
                this.reduced = new BigRational(this.numerator.divide(gcd), this.denominator.divide(gcd), false);
            }
            return this.reduced;
        } else {
            return this;
        }
    }

    public BigRational mul(BigRational other) {
        return new BigRational(this.numerator.multiply(other.numerator), this.denominator.multiply(other.denominator),
                true);
    }

    public BigRational div(BigRational other) {
        return new BigRational(this.numerator.multiply(other.denominator), this.denominator.multiply(other.numerator),
                true);
    }

    public BigRational add(BigRational other) {
        return new BigRational(this.numerator.multiply(other.denominator).add(this.denominator.multiply(other.numerator)),
                this.denominator.multiply(other.denominator), true);
    }

    public BigRational sub(BigRational other) {
        return new BigRational(this.numerator.multiply(other.denominator).subtract(this.denominator.multiply(other.numerator)),
                this.denominator.multiply(other.denominator), true);
    }

    public BigRational pow(int exponent) {
        return new BigRational(this.numerator.pow(exponent), this.denominator.pow(exponent), true);
    }

    public BigRational negate() {
        return new BigRational(this.numerator.negate(), this.denominator, this.neverReduced);
    }

    public boolean isInteger() {
        return this.reduce().denominator.equals(BigInteger.ONE);
    }

    public static BigRational min(BigRational a, BigRational b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.compareTo(b) <= 0 ? a : b;
    }

    public static BigRational max(BigRational a, BigRational b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.compareTo(b) >= 0 ? a : b;
    }

    @Override
    public int compareTo(BigRational o) {
        return this.numerator.multiply(o.denominator)
                .compareTo(o.numerator.multiply(this.denominator));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BigRational bigRat = (BigRational) o;

        if (this.neverReduced || bigRat.neverReduced) {
            return this.reduce().equals(bigRat.reduce());
        } else {
            return this.numerator.equals(bigRat.numerator) && this.denominator.equals(bigRat.denominator);
        }
    }

    @Override
    public int hashCode() {
        if (this.neverReduced) {
            return this.reduce().hashCode();
        } else {
            return Objects.hash(this.numerator, this.denominator);
        }
    }

    @Override
    public String toString() {
        return this.denominator.equals(BigInteger.ONE) ? this.numerator.toString() : this.numerator + "/" + this.denominator;
    }
}
