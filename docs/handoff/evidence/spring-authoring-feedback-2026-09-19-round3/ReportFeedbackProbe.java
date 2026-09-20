import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import telamin.fluxtion.audit.analyser.analyser.report.*;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.graph.Expr;
import telamin.fluxtion.audit.analyser.analyser.model.KV;

public class ReportFeedbackProbe {
  public static void main(String[] args) throws Exception {
    var store = new HeapLogStore(Files.readString(Path.of(args[0])));
    var sections = List.of(ReportSpec.SectionSpec.topology("Desk: everything that reaches the hedger"),
        ReportSpec.SectionSpec.series(Map.of("key", "pnlCalculator.totalPnl")), ReportSpec.SectionSpec.finding(42));
    var spec = new ReportSpec("probe", "Validation", "", "", null, FilterSnapshot.all(), sections);
    var resolved = ReportResolver.resolve(spec, store.index(), Map.of(42,new Finding(42,"Expected behaviour",null)),
        Set.of(), Set.of("Desk: everything that reaches the hedger"), new FilterState());
    // Same content shape as MainFrame.renderReportPdf's TOPOLOGY and SERIES branches.
    var content=List.of(new ReportRenderer.SectionContent("Focus",List.of("TOPOLOGY_FALLBACK_WITNESS"),null,null),
        new ReportRenderer.SectionContent("Series",List.of("SERIES_FALLBACK_WITNESS"),null,null),
        ReportRenderer.SectionContent.EMPTY);
    var pdf = new String(ReportRenderer.render(spec,resolved,content,"audit.yaml",null),StandardCharsets.ISO_8859_1);
    System.out.println("resolved="+resolved.sections().stream().map(r->r.kind()+":"+r.resolved()+":warning="+r.warning()).toList());
    System.out.println("topology fallback present="+pdf.contains("TOPOLOGY_FALLBACK_WITNESS"));
    System.out.println("series fallback present="+pdf.contains("SERIES_FALLBACK_WITNESS"));
    System.out.println("expected behaviour headed WHAT IS WRONG="+pdf.contains("WHAT IS WRONG"));
    try { Expr.parse("orderGateway.decision == \"REJECT\""); System.out.println("text equality parsed"); }
    catch(IllegalArgumentException e){System.out.println("text equality rejected="+e.getMessage());}
    var rows=ReportSpec.SectionSpec.table(Map.of("verb","read","recordIndex",69,"count",6,
        "fields",List.of("orderGateway.decision")),List.of(),null,null);
    var read=ReportVerb.assembleTable(rows,store);
    System.out.println("read table rows="+read.table().rows());
    var stats=ReportVerb.assembleTable(ReportSpec.SectionSpec.table(Map.of("verb","series","expr","pnlCalculator.totalPnl"),List.of(),null,null),store);
    System.out.println("table over series rows="+stats.table().rows().size()+"; notes="+stats.notes());
    for(boolean quoted:List.of(false,true)){
      var value=new KV("enabled","true",quoted);
      System.out.println("true quoted="+quoted+" kind="+value.kind()+" graphValue="+value.graphValue());
    }
  }
}
