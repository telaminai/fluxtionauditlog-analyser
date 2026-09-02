# The Spring route works. The blocker was mine.

**This file previously reported an upstream bug: that no property on `FluxtionSpringConfig` was
writable, that `logLevel` could not enable the audit log, and that `springToFluxtion` was therefore
unusable with the published contract. All of that was wrong.** The cause was a defect in the scaffold
script I wrote an hour earlier. The record is corrected here rather than deleted.

## What actually happened

My `scaffold.sh` reads the bean file and writes a shell class for every bean whose class is missing. It
checked only whether a **source file** existed. `fluxtionSpringConfig` is a bean like any other, so the
script decided `com.telamin.fluxtion.builder.extern.spring.FluxtionSpringConfig` was missing and
**generated a stub of it into the project**:

```
src/main/java/com/telamin/fluxtion/builder/extern/spring/FluxtionSpringConfig.java
```

A stub with no setters. It shadowed the real framework class, and Spring correctly reported every
property as not writable. The error pointed at Fluxtion; the fault was upstream of it by one directory.

## How it was found

`java.beans.Introspector` said all four properties were writable when loading from the jar. Loading the
same XML with Spring 6.2.7 standalone **succeeded** — until `target/classes` was added to the
classpath, at which point it failed identically. That one difference located it.

## The fix

The generator now resolves the dependency classpath and skips any bean whose class already exists:

```
skip com.telamin.fluxtion.builder.extern.spring.FluxtionSpringConfig — provided by a dependency
```

## Verified working

```
 ev  event     nodes that ran, in dispatch order
  1  LIMIT     ['limitStore']                        <- reference data: alert not triggered
  2  READING   ['sensorState', 'thresholdAlert']
  3  READING   ['sensorState']                       <- unchanged: cycle arrested
  4  READING   ['sensorState', 'thresholdAlert']
  5  LIMIT     ['limitStore']
decisions emitted:  4,ALERT,SENSOR-1
```

`mvn clean test` green, 4 tests including `GraphExistsTest`. **The audit log is enabled from XML with
`<property name="logLevel" value="INFO"/>`, exactly as the published contract documents.** So the whole
harness — `GraphExistsTest`, `trace.sh`, the staged build order — works unchanged on the Spring route.

## What remains genuinely undocumented

Two real requirements, both hard stops, neither in the design directory or the published contract:

1. **Spring must be on the *plugin's* classpath**, as `<dependencies>` of the plugin itself. Without it:
   `A required class was missing … org/springframework/context/ApplicationContext`.
2. **`springToFluxtion` must be declared before** any compiler execution bound to `process-classes`,
   or the second compile pass runs before generation.

## The lesson, which is not about Spring

I spent a long stretch building a case against the framework from an error my own tool caused, and got
as far as writing a reproduction and an upstream ticket. Two things would have caught it immediately:
**generating into a framework package is never right**, and the first question about a "not found" or
"not writable" symptom should be *what else is on the classpath* — which is exactly what the owner
asked when he saw the report.
