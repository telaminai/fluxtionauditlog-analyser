#!/usr/bin/env python3
"""Resolve a Fluxtion bean file from jar-manifest descriptors, deterministically.

The claim under test: the WIRING half of component integration needs no language model. Given
manifests that declare what each entry point provides, requires and consumes, and given the set of
figures the business asks for, both the SELECTION and the WIRING are a constraint solve.

What this does NOT do is judgement. Where two entry points are indistinguishable on the declared
surface, this reports the ambiguity and stops rather than guessing -- which is exactly the point:
it draws the line between the half a resolver can do and the half that needs a model.

Usage:
    bean-resolver.py --manifests DIR --figures f1,f2,... [--conventions k=v,...] [--xml OUT]
    bean-resolver.py --jars DIR      --figures f1,f2,... [--conventions k=v,...] [--xml OUT]

Both --manifests and --jars take a DIRECTORY.
"""
from __future__ import annotations
import argparse, itertools, pathlib, re, subprocess, sys, zipfile
from dataclasses import dataclass, field as dc_field


@dataclass(eq=False)   # identity semantics: two entry points are the same only if the same object
class EntryPoint:
    jar: str
    cls: str
    provides: dict           # field name -> interface (or None for a bare field)
    requires: list           # interface names, in no particular order
    ctor: list               # interface names, IN CONSTRUCTOR ORDER
    consumes: list
    description: str = ""
    conventions: dict = dc_field(default_factory=dict)

    @property
    def simple(self) -> str:
        return self.cls.rsplit(".", 1)[-1]

    @property
    def figures(self) -> set:
        return set(self.provides)

    @property
    def interfaces(self) -> set:
        return {i for i in self.provides.values() if i}

    def field_for(self, iface: str):
        for f, i in self.provides.items():
            if i == iface:
                return f
        return None


def unfold(text: str) -> str:
    r"""Manifest values wrap at 72 bytes and continue with a leading space.

    CRLF matters: `jar` writes \r\n, and a trailing \r defeats `(\S+)$` under re.M, so the
    line endings are normalised before anything else looks at the text.
    """
    return re.sub(r"\r?\n ", "", text.replace("\r\n", "\n"))


def parse_manifest(text: str, jar: str) -> list:
    text = unfold(text)
    title = re.search(r"^Implementation-Title:\s*(.+)$", text, re.M)
    jar_name = title.group(1).strip() if title else jar
    eps = []
    for block in re.split(r"\n\s*\n", text):
        if "Fluxtion-Entry-Point: true" not in block:
            continue
        m = re.search(r"^Name:\s*(\S+)$", block, re.M)
        if not m:
            continue
        cls = m.group(1).strip().removesuffix(".class").replace("/", ".")

        def grab(key):
            g = re.search(rf"^{key}:\s*(.*)$", block, re.M)
            return g.group(1).strip() if g else ""

        provides = {}
        for item in [p.strip() for p in grab("Fluxtion-Provides").split(",") if p.strip()]:
            if "=" in item:
                f, i = item.split("=", 1)
                provides[f.strip()] = i.strip()
            else:
                provides[item] = None
        requires = [r.strip() for r in grab("Fluxtion-Requires").split(",") if r.strip()]
        ctor_raw = grab("Fluxtion-Constructor").strip("()")
        ctor = [c.strip() for c in ctor_raw.split(",") if c.strip()]
        consumes = [c.strip() for c in grab("Fluxtion-Consumes").split(",") if c.strip()]
        conventions = {}
        for item in [c.strip() for c in grab("Fluxtion-Convention").split(",") if c.strip()]:
            if "=" in item:
                f, v = item.split("=", 1)
                conventions[f.strip()] = v.strip()
        eps.append(EntryPoint(jar_name, cls, provides, requires, ctor, consumes,
                              grab("Fluxtion-Description"), conventions))
    return eps


def load(manifest_dir=None, jar_dir=None) -> list:
    eps = []
    if manifest_dir:
        for p in sorted(pathlib.Path(manifest_dir).glob("*.mf")):
            eps += parse_manifest(p.read_text(), p.stem)
    if jar_dir:
        for p in sorted(pathlib.Path(jar_dir).glob("*.jar")):
            with zipfile.ZipFile(p) as z:
                try:
                    eps += parse_manifest(z.read("META-INF/MANIFEST.MF").decode(), p.stem)
                except KeyError:
                    pass
    return eps


def solve(eps: list, wanted: set, profile: dict | None = None):
    """Every minimal selection satisfying: figures covered, and every requirement met."""
    by_jar = {}
    for e in eps:
        by_jar.setdefault(e.jar, []).append(e)
    jars = sorted(by_jar)
    # A jar is a CATALOGUE: it may contribute any subset of its entry points, including more than
    # one. The earlier "one per jar" assumption was fixture-shaped -- two independent entry points
    # in one jar, each needed for a different figure, returned UNSATISFIABLE. (Review F4.1.)
    options = []
    for j in jars:
        subsets = [()]
        for e in by_jar[j]:
            subsets = subsets + [t + (e,) for t in subsets]
        options.append(subsets)
    valid = []
    profile = profile or {}
    for combo in itertools.product(*options):
        sel = [e for grp in combo for e in grp]
        if not sel:
            continue
        # A site profile fixes a convention for a figure. An entry point that PUBLISHES that figure
        # must DECLARE a matching convention; silence is not a match. This mirrors exactly how the
        # model ruled PricingFull out in round 55 -- on absence of a promise, not presence of a word.
        if any(f in e.figures and e.conventions.get(f) != v
               for e in sel for f, v in profile.items()):
            continue
        figures = set().union(*(e.figures for e in sel))
        if not wanted <= figures:
            continue
        available = set().union(*(e.interfaces for e in sel))
        if not all(set(e.requires) <= available for e in sel):
            continue
        # every constructor argument must be satisfiable by some selected field
        if any(not any(o.field_for(i) for o in sel) for e in sel for i in e.ctor):
            continue
        # PROVIDER UNIQUENESS. If two selected components publish the same interface, the wiring
        # is ambiguous even when the component set is not: the emitter would silently pick the
        # lexically later one. Reject the combination rather than guess. (Review F4.2.)
        seen_iface = {}
        clash = False
        for e in sel:
            for iface in e.interfaces:
                if iface in seen_iface:
                    clash = True
                seen_iface[iface] = e
        if clash:
            continue
        valid.append(sel)
    if not valid:
        coverable = set().union(*(e.figures for e in eps)) if eps else set()
        missing = sorted(wanted - coverable)
        if missing:
            return [], "UNPROVIDED:" + ",".join(missing)
        # Distinguish "no consistent combination" from "the manifests carry no figure=Interface
        # mapping at all", which is what a v1 catalogue looks like and which previously reported
        # the wrong cause. (Review F2.)
        mapped = any(i for e in eps for i in e.provides.values() if i)
        if not mapped:
            return [], ("the manifests declare no `figure=Interface` mappings, so no requirement can "
                        "be matched to a provider -- this catalogue predates the `Fluxtion-Provides: "
                        "name=Api` convention")
        return [], "no selection satisfies the required figures (all figures exist, but no combination is consistent)"
    fewest = min(len(s) for s in valid)
    valid = [s for s in valid if len(s) == fewest]
    # prefer the least over-provisioned selection: fewest figures beyond what was asked for
    def excess(s):
        return len(set().union(*(e.figures for e in s)) - wanted)
    low = min(excess(s) for s in valid)
    return [s for s in valid if excess(s) == low], None


def order(sel: list) -> list:
    """Topological: a bean is emitted after everything it constructs from."""
    provider = {}
    for e in sel:
        for iface in e.interfaces:
            provider[iface] = e
    done, out = set(), []
    remaining = list(sel)
    while remaining:
        progressed = False
        for e in list(remaining):
            deps = {provider[i] for i in e.ctor if i in provider and provider[i] is not e}
            if deps <= done:
                out.append(e); done.add(e); remaining.remove(e); progressed = True
        if not progressed:
            # A constructor cycle cannot be instantiated by Spring. Emitting it in declared order
            # produced a bean file that always fails at runtime. Fail loudly instead. (Review F4.3.)
            names = ", ".join(sorted(e.simple for e in remaining))
            raise ValueError("constructor cycle between: " + names)
    return out


def bean_id(e: EntryPoint, selection=None) -> str:
    """Jar name when that jar contributes exactly one entry point, else jar+class.

    Allowing several entry points from one jar (review F4.1) made the jar name ambiguous and
    emitted duplicate bean ids. Disambiguating ONLY when needed keeps single-entry-point
    catalogues -- including the round-48 fixture -- byte-identical to their previous output.
    """
    base = re.sub(r"[^A-Za-z0-9]", "", e.jar)
    base = base[0].lower() + base[1:] if base else "bean"
    if selection is not None and sum(1 for o in selection if o.jar == e.jar) > 1:
        return base + e.simple
    return base


def emit_xml(sel: list, audit_level: str = None) -> str:
    provider = {}
    for e in sel:
        for iface in e.interfaces:
            provider[iface] = e
    lines = ['<?xml version="1.0" encoding="UTF-8"?>',
             '<beans xmlns="http://www.springframework.org/schema/beans"',
             '       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"',
             '       xsi:schemaLocation="http://www.springframework.org/schema/beans',
             '       https://www.springframework.org/schema/beans/spring-beans.xsd">',
             '']
    for e in order(sel):
        bid = bean_id(e, sel)
        if not e.ctor:
            lines.append(f'  <bean id="{bid}" class="{e.cls}"/>')
        else:
            lines.append(f'  <bean id="{bid}" class="{e.cls}">')
            for iface in e.ctor:
                src = provider.get(iface)
                fld = src.field_for(iface) if src else None
                ref = f"#{{{bean_id(src, sel)}.{fld}}}" if src else f"UNRESOLVED:{iface}"
                lines.append(f'    <constructor-arg value="{ref}"/>')
            lines.append('  </bean>')
        lines.append('')
    if audit_level:
        # Enables the audit log for the generated processor. FluxtionSpring.addNodes reads this
        # property and calls EventProcessorConfig.addEventAudit with it.
        lines.append('  <bean class="com.telamin.fluxtion.builder.extern.spring.FluxtionSpringConfig">')
        lines.append(f'    <property name="logLevel" value="{audit_level}"/>')
        lines.append('  </bean>')
        lines.append('')
    lines.append('</beans>')
    return "\n".join(lines)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--manifests")
    ap.add_argument("--jars")
    ap.add_argument("--figures", required=True,
                    help="comma-separated figure names the business requires")
    ap.add_argument("--xml", help="write the bean file here")
    ap.add_argument("--audit", metavar="LEVEL",
                    help="emit a FluxtionSpringConfig bean with this logLevel (INFO, ERROR, NONE...). "
                         "The Spring route DOES expose audit configuration -- see UP-FLX-47, withdrawn.")
    ap.add_argument("--conventions", default="",
                    help="site profile, e.g. 'spread=hedged' -- fixes a convention per figure")
    a = ap.parse_args()

    eps = load(a.manifests, a.jars)
    wanted = {f.strip() for f in a.figures.split(",") if f.strip()}
    print(f"  {len(eps)} entry points across {len({e.jar for e in eps})} jars")
    print(f"  {len(wanted)} required figures\n")

    profile = dict(c.split("=", 1) for c in a.conventions.split(",") if "=" in c)
    if profile:
        print(f"  site profile: " + ", ".join(f"{k}={v}" for k, v in profile.items()) + "\n")
    try:
        solutions, err = solve(eps, wanted, profile)
    except ValueError as cycle:
        print(f"  UNSATISFIABLE: {cycle}")
        return 2
    if err:
        print(f"  UNSATISFIABLE: {err}")
        return 2
    if len(solutions) > 1:
        print(f"  AMBIGUOUS — {len(solutions)} equally minimal selections. "
              f"The declared surface does not decide; this needs judgement.\n")
        for i, s in enumerate(solutions, 1):
            print(f"    candidate {i}: " + ", ".join(sorted(e.simple for e in s)))
        # show exactly which slot is undecided
        for jar in sorted({e.jar for s in solutions for e in s}):
            picks = {e.simple for s in solutions for e in s if e.jar == jar}
            if len(picks) > 1:
                print(f"\n    undecided jar '{jar}': {len(picks)} candidates")
                for p in sorted(picks):
                    d = next(e.description for e in eps if e.simple == p)
                    print(f"      {p:18} {d}")
        return 3

    sel = solutions[0]
    try:
        ordered = order(sel)
    except ValueError as cycle:
        print(f"  UNSATISFIABLE: {cycle}")
        return 2
    print("  RESOLVED — one minimal selection:\n")
    for e in ordered:
        print(f"    {e.jar:12} {e.simple}")
    xml = emit_xml(sel, a.audit)
    if a.xml:
        pathlib.Path(a.xml).write_text(xml + "\n")
        print(f"\n  wrote {a.xml}")
    else:
        print("\n" + xml)
    return 0


if __name__ == "__main__":
    sys.exit(main())
