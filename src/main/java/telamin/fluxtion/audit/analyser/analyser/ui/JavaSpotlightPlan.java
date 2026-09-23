package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.source.*;
import java.util.*;

/** Immutable preparation: no reveal, view mutation or selected-model publication. */
record JavaSpotlightPlan(SourceService.Lookup lookup, Map<String, Prepared> targets) {
    record Prepared(SpotlightTarget target, SourceDocument document, EventProcessorModel model) { }
    JavaSpotlightPlan { targets = Map.copyOf(targets); }

    static JavaSpotlightPlan read(SourceService.Lookup lookup, List<SpotlightTarget.Request> requests,
                                  List<SourceDocument> retained) {
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
                    && target.number() > document.text().split("\n", -1).length)
                throw new IllegalArgumentException("Line " + target.number() + " is outside " + document.identity());
            identities.add(document.identity());
            if (identities.size() > 1) throw new IllegalArgumentException("Java spotlights must name one document; use separate calls");
            targets.put(target.name(), new Prepared(target, document, EventProcessorModel.parse(target.sourceFqn(), document.text())));
        }
        return new JavaSpotlightPlan(lookup, targets);
    }
}
