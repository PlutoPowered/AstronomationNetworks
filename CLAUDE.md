# AstronomationNetworks

Algorithmic core for a factory-building game. Goal: "solve" automation networks — buildings connected by
conveyor belts/pipes/etc — so the game can determine exactly what a production line will do, rather than
relying solely on brute-force per-tick simulation.

Java library (Gradle, `java-library` + `maven-publish`), Java 24 toolchain, JUnit 5.

## Build / test

```
./gradlew build
./gradlew test
./gradlew htmlJavadocs   # writes to /docs
```

## Hard requirement: exact arithmetic

All quantities are `com.astronomation.networks.math.BigRational`, never floating point. This is
non-negotiable: the goal is to avoid game states where input/output ratios are ever slightly off, which
floating point cannot guarantee. Any new numeric code in this project must use `BigRational`.

## Package layout

- `math` — `BigRational`, the exact-arithmetic primitive everything else is built on.
- `math.simplex` — a generic Big-M Simplex LP solver (`SimplexBuilder` → `SimplexTableau`/`SimplexMatrix`).
  Not factory-domain-specific; just an LP toolkit.
- `math.graph` — `LinearMatrixNetworkNode`/`Edge` + `LinearMatrixBuilder`, which turns a graph of factory
  nodes (`PRODUCER`, `MACHINE`, `SPLITTER`, `MERGER`, `SINK`) into an LP: one flow variable per directed
  edge, conservation constraints per node type, machine recipe ratios enforced via a per-machine rate
  variable (`flow_edge = quantity * r_machine`), capacity constraints per edge, objective = maximize total
  producer output. This solves for a **steady-state** flow — splitter/merger splits are left continuous and
  underdetermined (no discrete round-robin at this layer). See `NetworkBuilderTests` for worked examples
  (2-node line, machine ratio, splitter tree, merger tree, cross-branch bottleneck, loop-back cycle).
- `model` / `model.plan` — `Network`/`TickPlan`: the tick-level model, currently still interfaces
  (work in progress, no implementation yet). This is the layer meant to simulate discrete ticks (buffers,
  belt contents, discrete splitter/merger allocation) and answer "what happens on this exact tick."

The LP layer (`math.graph`) and the tick layer (`model.plan`) answer different questions: the LP gives you
the achievable steady-state throughput of a network; the tick layer is meant to give you the actual
tick-by-tick behavior, including transients and any oscillation that discrete splitter/merger allocation
introduces. The Simplex solver may or may not end up being invoked from the tick layer — simple networks
(e.g. a static electrical-style consumption model) may not need it at all.

## `TickPlan` design

`TickPlan` (`model/plan/TickPlan.java`) represents a network's tick-by-tick behavior as it evolves toward a
long-run pattern:

- `preCycle()` — the transient `List<Sentinel>` before the network settles.
- `terminalCycle()` — the full **minimal repeating period**, as a `List<Sentinel>`. The period length is
  just the list size; there's no separate period field.
- `terminalAverage()` — a single derived `Sentinel` giving the terminal cycle's per-tick rate (the cycle's
  deltas averaged down to one tick). This is a convenience view for callers that only care about long-run
  throughput; it is never the canonical representation, because averaging away *when* items arrive within a
  period discards information other consumers may need (buffer feasibility within the period, or correctly
  composing this network's output as another network's input, which needs actual phase/LCM alignment, not
  just mean rates).

A `Sentinel` holds a `Set<Delta>` per `Network.Node`; a `Delta` is an `item` + `BigRational quantity()`.

**Global unit convention: every `Delta.quantity()`, everywhere, is a per-tick rate** — not a per-period
total, not an absolute count. This applies uniformly across `preCycle()`, `terminalCycle()`, and
`terminalAverage()`, so any `Sentinel` from any of the three is directly comparable to any other.

### Why a terminal state is guaranteed to exist

Item sources are speed-limited by design (bounded items/sec), which rules out unbounded divergence (buffers
can't grow forever). Given that, plus a deterministic tick-update function, the tick state space is finite,
so by pigeonhole every network's trajectory is guaranteed to eventually reach either an idle/empty state or
a repeating cycle of some period ≥ 1 — a true fixed point is just the period-1 special case. **Oscillation
(period > 1) should be expected, not treated as a bug**: it arises from how the tick simulator resolves
splitters/mergers (e.g. strict round-robin discrete allocation), not from the LP layer, which sidesteps the
issue by treating splits as continuous. A deterministic, well-chosen tie-break rule at each
splitter/merger keeps the period bounded and predictable (e.g. related to the LCM of recipe ratio
denominators) rather than chaotic.

Whether `terminalCycle()` returns an empty list or a period-1 zero-delta `Sentinel` for the "petered out"
case is left to implementation — either is acceptable; consumers should handle both.

### Open work

- No implementation yet for the tick simulator itself: buffer/belt state representation, discrete
  splitter/merger allocation rules, and cycle detection (state-hashing or Floyd's/Brent's algorithm over
  full tick state — buffer contents + in-flight belt/pipe quantities, not just deltas) to actually populate
  `preCycle()`/`terminalCycle()`.
- Worth capping simulation length and reporting "no cycle found within N ticks" as a safety valve, even
  though a cycle's existence is theoretically guaranteed — periods can be large (LCM of several
  machine/splitter cycle lengths).
