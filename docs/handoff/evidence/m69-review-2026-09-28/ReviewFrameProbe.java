package telamin.fluxtion.audit.analyser.analyser.ui;

import java.nio.file.*;
import java.util.*;
import telamin.fluxtion.audit.analyser.analyser.session.*;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.*;
import static telamin.fluxtion.audit.analyser.analyser.ui.WalkPlaybackFrameTest.*;

/** Reviewer probe on an isolated real frame; no participant files or stored credentials. */
public class ReviewFrameProbe {
    static Map<String,Object> target(String name,String caption) { return Map.of("target",name,"caption",caption); }
    static void call(AsyncOpenInterleavingFrameTest.Frame f,String verb,Map<String,Object> params) throws Exception {
        onEdt(() -> {var r=render(f.ex,verb,params);if (!Boolean.TRUE.equals(r.get("ok"))) throw new AssertionError(r.toString());});
    }
    public static void main(String[] args) throws Exception {
        try {
            try (var f=opened(Files.createTempDirectory("m69-review-demo-"))) {
                var overlay=(SpotlightOverlay)field(f.frame,"spotlight");
                var session=(SessionDriver)field(f.frame,"session");
                var partial=Map.of("view",Map.of("tab","topology"),"targets",List.of(
                        target("topology:node:DEMO_missing","unavailable first"),target("topology:node:priceListener","second")));
                call(f,"walk",Map.of("name","DEMO_partial","steps",List.of(partial)));
                call(f,"walk",Map.of("name","DEMO_partial","play",true));
                await("partial lit",() -> "PARTLY_SHOWN".equals(walk(f).phase()) && overlay.isLit());
                onEdt(() -> System.out.println("numbering: session="+walk(f).targets().stream().map(t -> t.n()+":"+t.available()).toList()
                        +", overlay="+overlay.lit().stream().map(l -> l.n()+":"+l.caption()).toList()));
                call(f,"walk",Map.of("end",true));
                var record=Map.of("view",Map.of("tab","summary","record",0),"targets",List.of(target("records:row:0","record claim")));
                call(f,"walk",Map.of("name","DEMO_record","steps",List.of(record)));
                call(f,"walk",Map.of("name","DEMO_record","play",true));
                await("record lit",() -> "SHOWN".equals(walk(f).phase()) && overlay.isLit());
                long ticket=walk(f).ticket();
                onEdt(() -> session.post(new SessionEvents.LogIdentityObserved(session.snapshot().logGeneration(),"UNVERIFIED","DEMO identity probe")));
                await("identity re-resolved",() -> walk(f).ticket()>ticket && !"PREPARING".equals(walk(f).phase()));
                onEdt(() -> System.out.println("identity: session="+session.snapshot().logIdentity()+", target="+walk(f).targets().get(0).state()
                        +", available="+walk(f).targets().get(0).available()+", lit="+overlay.isLit()));
                call(f,"walk",Map.of("end",true));
                var hidden=Map.of("view",Map.of("tab","summary","record",1,"filter",Map.of("dimensions",List.of())),
                        "targets",List.of(target("detail","record one claim")));
                call(f,"walk",Map.of("name","DEMO_hidden","steps",List.of(hidden)));
                call(f,"walk",Map.of("name","DEMO_hidden","play",true));
                await("hidden record decision",() -> !"PREPARING".equals(walk(f).phase()));
                onEdt(() -> System.out.println("hidden detail: phase="+walk(f).phase()+", target="+walk(f).targets().get(0).state()
                        +", available="+walk(f).targets().get(0).available()+", selection="
                        +Arrays.toString(((LogTablePanel)field(f.frame,"tablePanel")).selectedModelRows())+", reason="+walk(f).reason()));
                call(f,"walk",Map.of("end",true));
                var structural=Map.of("view",Map.of("tab","topology"),"targets",List.of(target("topology:node:priceListener","original")));
                call(f,"walk",Map.of("name","DEMO_edit","steps",List.of(structural,structural)));
                call(f,"walk",Map.of("name","DEMO_edit","play",true));
                await("original shown",() -> "SHOWN".equals(walk(f).phase()) && overlay.isLit());
                call(f,"walk",Map.of("name","DEMO_edit","steps",List.of(Map.of("targets",List.of(target("status","replacement"))))));
                onEdt(() -> System.out.println("replace active: published count="+walk(f).count()+", saved count="
                        +((telamin.fluxtion.audit.analyser.analyser.config.AppConfig)field(f.frame,"config")).walks.stream()
                         .filter(w->w.name().equals("DEMO_edit")).findFirst().orElseThrow().steps().size()+", lit="+overlay.lit().get(0).caption()));
                onEdt(() -> {
                    var result=render(f.ex,"walk",Map.of("name","DEMO_edit","play",true,"step",99));
                    System.out.println("out-of-range play while active: ok="+result.get("ok")+", node reason="+walk(f).reason());
                });
            }
            System.exit(0);
        } catch (Throwable e) { e.printStackTrace();System.exit(1); }
    }
}
