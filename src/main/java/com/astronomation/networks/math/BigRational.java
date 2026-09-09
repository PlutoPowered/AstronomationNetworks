package com.astronomation.networks.math;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Objects;

public final class BigRational implements Comparable<BigRational> {
    public static final BigRational ZERO = BigRational.of(0);
    public static final BigRational ONE = BigRational.of(1);
    public static final BigRational NEGATIVE_ONE = BigRational.of(-1);

    //Primary (fast-path) representation; valid iff numeratorBig == null
    private long numerator;
    private long denominator;

    //Fallback representation, only allocated once the value overflows a long
    private BigInteger numeratorBig;
    private BigInteger denominatorBig;

    //Transient fields
    private boolean neverReduced = true;
    private BigRational reduced = null;

    private BigRational(long numerator, long denominator, boolean neverReduced) {
        if (denominator < 0) {
            denominator = -denominator;
            numerator = -numerator;
        }

        this.numerator = numerator;
        this.denominator = denominator;
        this.neverReduced = neverReduced;
    }

    private BigRational(BigInteger numerator, BigInteger denominator, boolean neverReduced) {
        if (denominator.signum() == -1) {
            denominator = denominator.negate();
            numerator = numerator.negate();
        }

        if (fitsInLong(numerator) && fitsInLong(denominator)) {
            this.numerator = numerator.longValueExact();
            this.denominator = denominator.longValueExact();
        } else {
            this.numeratorBig = numerator;
            this.denominatorBig = denominator;
        }

        this.neverReduced = neverReduced;
    }

    public BigRational(BigInteger numerator, BigInteger denominator) {
        this(numerator, denominator, true);
    }

    private static boolean fitsInLong(BigInteger value) {
        return value.bitLength() <= 63;
    }

    private boolean isLong() {
        return this.numeratorBig == null;
    }

    private BigInteger numeratorBig() {
        return this.isLong() ? BigInteger.valueOf(this.numerator) : this.numeratorBig;
    }

    private BigInteger denominatorBig() {
        return this.isLong() ? BigInteger.valueOf(this.denominator) : this.denominatorBig;
    }

    private static long gcd(long a, long b) {
        a = Math.abs(a);
        b = Math.abs(b);
        while (b != 0) {
            long t = b;
            b = a % b;
            a = t;
        }
        return a;
    }

    public static BigRational of(long val) {
        return new BigRational(val, 1L, false);
    }

    public static BigRational of(long numerator, long denominator) {
        return new BigRational(numerator, denominator, true);
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
        return new BigDecimal(this.numerator())
                .divide(new BigDecimal(this.denominator()), scale, roundingMode);
    }

    public int signum() {
        return this.isLong() ? Long.signum(this.numerator) : this.numeratorBig.signum();
    }

    public BigInteger numerator() {
        return this.numeratorBig();
    }

    public BigInteger denominator() {
        return this.denominatorBig();
    }

    public boolean neverReduced() {
        return this.neverReduced;
    }

    public BigRational reduce() {
        if (!this.neverReduced) {
            return this;
        }
        if (this.reduced == null) {
            if (this.isLong()) {
                long gcd = gcd(this.numerator, this.denominator);
                this.reduced = new BigRational(this.numerator / gcd, this.denominator / gcd, false);
            } else {
                BigInteger gcd = this.numeratorBig.gcd(this.denominatorBig);
                this.reduced = new BigRational(this.numeratorBig.divide(gcd), this.denominatorBig.divide(gcd), false);
            }
        }
        return this.reduced;
    }

    public BigRational mul(BigRational other) {
        if (this.signum() == 0 || other.signum() == 0) {
            return ZERO;
        }

        if (this.isLong() && other.isLong()) {
            try {
                long gcd1 = gcd(this.numerator, other.denominator);
                long gcd2 = gcd(other.numerator, this.denominator);

                long numerator = Math.multiplyExact(this.numerator / gcd1, other.numerator / gcd2);
                long denominator = Math.multiplyExact(this.denominator / gcd2, other.denominator / gcd1);

                return new BigRational(numerator, denominator, true);
            } catch (ArithmeticException overflow) {
                //fall through to the BigInteger path below
            }
        }

        BigInteger thisNum = this.numeratorBig();
        BigInteger thisDenom = this.denominatorBig();
        BigInteger otherNum = other.numeratorBig();
        BigInteger otherDenom = other.denominatorBig();

        BigInteger gcd1 = thisNum.gcd(otherDenom);
        BigInteger gcd2 = otherNum.gcd(thisDenom);

        BigInteger numerator = thisNum.divide(gcd1).multiply(otherNum.divide(gcd2));
        BigInteger denominator = thisDenom.divide(gcd2).multiply(otherDenom.divide(gcd1));

        return new BigRational(numerator, denominator, true);
    }

    public BigRational div(BigRational other) {
        if (this.signum() == 0 && other.signum() != 0) {
            return ZERO;
        }

        if (this.isLong() && other.isLong()) {
            try {
                long gcd1 = gcd(this.numerator, other.numerator);
                long gcd2 = gcd(other.denominator, this.denominator);

                long numerator = Math.multiplyExact(this.numerator / gcd1, other.denominator / gcd2);
                long denominator = Math.multiplyExact(this.denominator / gcd2, other.numerator / gcd1);

                return new BigRational(numerator, denominator, true);
            } catch (ArithmeticException overflow) {
                //fall through to the BigInteger path below
            }
        }

        BigInteger thisNum = this.numeratorBig();
        BigInteger thisDenom = this.denominatorBig();
        BigInteger otherNum = other.numeratorBig();
        BigInteger otherDenom = other.denominatorBig();

        BigInteger gcd1 = thisNum.gcd(otherNum);
        BigInteger gcd2 = otherDenom.gcd(thisDenom);

        BigInteger numerator = thisNum.divide(gcd1).multiply(otherDenom.divide(gcd2));
        BigInteger denominator = thisDenom.divide(gcd2).multiply(otherNum.divide(gcd1));

        return new BigRational(numerator, denominator, true);
    }

    public BigRational add(BigRational other) {
        if (other.signum() == 0) {
            return this;
        }
        if (this.signum() == 0) {
            return other;
        }

        if (this.isLong() && other.isLong()) {
            try {
                if (this.denominator == other.denominator) {
                    return new BigRational(Math.addExact(this.numerator, other.numerator), this.denominator, true);
                }
                long numerator = Math.addExact(Math.multiplyExact(this.numerator, other.denominator), Math.multiplyExact(this.denominator, other.numerator));
                long denominator = Math.multiplyExact(this.denominator, other.denominator);
                return new BigRational(numerator, denominator, true);
            } catch (ArithmeticException overflow) {
                //fall through to the BigInteger path below
            }
        }

        BigInteger thisNum = this.numeratorBig();
        BigInteger thisDenom = this.denominatorBig();
        BigInteger otherNum = other.numeratorBig();
        BigInteger otherDenom = other.denominatorBig();

        if (thisDenom.equals(otherDenom)) {
            return new BigRational(thisNum.add(otherNum), thisDenom, true);
        }

        return new BigRational(thisNum.multiply(otherDenom).add(thisDenom.multiply(otherNum)),
                thisDenom.multiply(otherDenom), true);
    }

    public BigRational sub(BigRational other) {
        if (other.signum() == 0) {
            return this;
        }

        if (this.isLong() && other.isLong()) {
            try {
                if (this.denominator == other.denominator) {
                    return new BigRational(Math.subtractExact(this.numerator, other.numerator), this.denominator, true);
                }
                long numerator = Math.subtractExact(Math.multiplyExact(this.numerator, other.denominator), Math.multiplyExact(this.denominator, other.numerator));
                long denominator = Math.multiplyExact(this.denominator, other.denominator);
                return new BigRational(numerator, denominator, true);
            } catch (ArithmeticException overflow) {
                //fall through to the BigInteger path below
            }
        }

        BigInteger thisNum = this.numeratorBig();
        BigInteger thisDenom = this.denominatorBig();
        BigInteger otherNum = other.numeratorBig();
        BigInteger otherDenom = other.denominatorBig();

        if (thisDenom.equals(otherDenom)) {
            return new BigRational(thisNum.subtract(otherNum), thisDenom, true);
        }

        return new BigRational(thisNum.multiply(otherDenom).subtract(thisDenom.multiply(otherNum)),
                thisDenom.multiply(otherDenom), true);
    }

    public BigRational pow(int exponent) {
        return new BigRational(this.numerator().pow(exponent), this.denominator().pow(exponent), true);
    }

    public BigRational negate() {
        if (this.isLong()) {
            try {
                return new BigRational(Math.negateExact(this.numerator), this.denominator, this.neverReduced);
            } catch (ArithmeticException overflow) {
                //fall through to the BigInteger path below
            }
        }
        return new BigRational(this.numeratorBig().negate(), this.denominatorBig(), this.neverReduced);
    }

    public boolean isInteger() {
        return this.reduce().denominatorBig().equals(BigInteger.ONE);
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
        if (this.isLong() && o.isLong()) {
            if (this.denominator == o.denominator) {
                return Long.compare(this.numerator, o.numerator);
            }
            try {
                long left = Math.multiplyExact(this.numerator, o.denominator);
                long right = Math.multiplyExact(o.numerator, this.denominator);
                return Long.compare(left, right);
            } catch (ArithmeticException overflow) {
                //fall through to the BigInteger path below
            }
        }

        BigInteger thisNum = this.numeratorBig();
        BigInteger thisDenom = this.denominatorBig();
        BigInteger oNum = o.numeratorBig();
        BigInteger oDenom = o.denominatorBig();

        if (thisDenom.equals(oDenom)) {
            return thisNum.compareTo(oNum);
        }

        return thisNum.multiply(oDenom).compareTo(oNum.multiply(thisDenom));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BigRational bigRat = (BigRational) o;

        if (this.neverReduced || bigRat.neverReduced) {
            return this.reduce().equals(bigRat.reduce());
        }
        if (this.isLong() && bigRat.isLong()) {
            return this.numerator == bigRat.numerator && this.denominator == bigRat.denominator;
        }
        return this.numeratorBig().equals(bigRat.numeratorBig()) && this.denominatorBig().equals(bigRat.denominatorBig());
    }

    @Override
    public int hashCode() {
        if (this.neverReduced) {
            return this.reduce().hashCode();
        }
        if (this.isLong()) {
            return Objects.hash(this.numerator, this.denominator);
        }
        return Objects.hash(this.numeratorBig, this.denominatorBig);
    }

    @Override
    public String toString() {
        if (this.isLong()) {
            return this.denominator == 1 ? Long.toString(this.numerator) : this.numerator + "/" + this.denominator;
        }
        return this.denominatorBig.equals(BigInteger.ONE) ? this.numeratorBig.toString() : this.numeratorBig + "/" + this.denominatorBig;
    }
}
