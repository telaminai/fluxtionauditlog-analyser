package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.source.*;
import java.util.*;

/** Immutable preparation: no reveal, view mutation or selected-model publication. */
record JavaSpotlightPlan(SourceService.Lookup lookup, Map<String, Prepared> targets) {
    record Prepared(SpotlightTarget target, SourceDocument document, EventProcessorModel model) { }
    JavaSpotlightPlan { targets = Map.copyOf(targets); }

    static JavaSpotlightPlan read(SourceService.Lookup lookup, List<SpotlightTarget.Request> requests,
                                  List<SourceDocument> retained) {
        return read(lookup, requests, retained, Map.of());
    }

    /**
     * {@code expected}: target name to a pinned {@code sha256:} revision (a shipped walk's {@code source} basis). A target
     * whose document is any other revision is refused whole, so its caption is never drawn over code it was not written
     * against.
     */
    static JavaSpotlightPlan read(SourceService.Lookup lookup, List<SpotlightTarget.Request> requests,
                                  List<SourceDocument> retained, Map<String, String> expected) {
        Map<String, SourceDocument> documents = new LinkedHashMap<>();
        Map<String, Prepared> targets = new LinkedHashMap<>();
        Set<String> identities = new HashSet<>();
        retained.forEach(d -> identities.add(d.identity()));
        for (var request : requests) {
            var target = SpotlightTarget.parse(request.target()).target();
            if (!target.javaSource()) continue;
            var document = documents.computeIfAbsent(target.sourceFqn(), fqn -> lookup.freshDocumentForSpotlight(fqn)
                    .orElseThrow(() -> new IllegalArgumentException("No Java source for " + fqn
                            + ". Repeating rereads files and entries in known sources jars; a new sources jar"
                            + " requires source reconfiguration or restart. Lookup: source-viewer, first-match.")));
            if (target.family() == SpotlightTarget.Family.JAVA_LINE
                    && target.lineTo() > document.text().split("\n", -1).length)
                // M80: a range whose LAST line is past the document's end is refused whole (not clamped) — the
                // same policy a single out-of-range line already had, said for the range it was asked for.
                throw new IllegalArgumentException("Line " + (target.lineFrom() == target.lineTo() ? String.valueOf(target.lineFrom())
                        : target.lineFrom() + "-" + target.lineTo()) + " is outside " + document.identity());
            String pinned = expected.get(target.name());
            if (pinned != null && !pinned.equals("sha256:" + document.revision()))
                throw new IllegalArgumentException(document.identity() + " is not the source revision this caption was written"
                        + " against, so it is not pointed at");
            identities.add(document.identity());
            if (identities.size() > 1) throw new IllegalArgumentException("Java spotlights must name one document; use separate calls");
            targets.put(target.name(), new Prepared(target, document, EventProcessorModel.parse(target.sourceFqn(), document.text())));
        }
        return new JavaSpotlightPlan(lookup, targets);
    }
}
