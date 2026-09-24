package telamin.fluxtion.audit.analyser.analyser.config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class GraphProfileMetadataTest {
 @TempDir Path dir;
 @Test void externalPathsKeepStyleAndClosedState() throws Exception {
  for(boolean marker:List.of(false,true)) {
   AppConfig seed=new AppConfig();
   var ext=marker?List.<GraphSpec.ExternalSpec>of():List.of(new GraphSpec.ExternalSpec("values.csv","ext","t","epochMillis",null,"v",0));
   var marks=marker?List.of(new GraphSpec.MarkerSpec("mark","circle",null,null,null,"events.csv","t","epochMillis",null,"v",null,0,"LOCF")):List.<GraphSpec.MarkerSpec>of();
   seed.savedGraphs.add(new GraphSpec("Chart",List.of(),List.of(),null,null,null,null,List.of(),List.of(),List.of(),List.of(),ext,marks,"points",false));
   Path file=ProjectProfile.pathFor(dir.resolve("project-"+marker));var share=new SettingsShare();ProjectProfile.save(file,seed,share);
   AppConfig back=new AppConfig();assertTrue(ProjectProfile.load(file,back,share).loaded());var actual=back.savedGraphs.getFirst();
   assertEquals("points",actual.style(),"external path rewrite must retain style");
   assertFalse(actual.open(),"external path rewrite must retain closed state");
  }
 }

 @Test void duplicateNamesRefuseWithoutChangingProfileOrTarget() throws Exception {
  Path file=ProjectProfile.pathFor(dir.resolve("duplicate"));
  java.nio.file.Files.createDirectories(file.getParent());
  String text="share.version=1\ngraph.count=2\ngraph.0.name=Same\ngraph.0.explanation=first\ngraph.1.name=Same\ngraph.1.explanation=second\n";
  java.nio.file.Files.writeString(file,text);
  AppConfig target=new AppConfig();target.savedGraphs.add(new GraphSpec("Unrelated",List.of(),List.of(),null,null,null,null,List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),"line",true));
  var before=List.copyOf(target.savedGraphs);
  var result=ProjectProfile.load(file,target,new SettingsShare());
  assertFalse(result.loaded(),"ambiguous profile must refuse rather than discard one definition");
  assertTrue(result.message().contains("Duplicate chart name"),result.message());
  assertEquals(before,target.savedGraphs);assertEquals(text,java.nio.file.Files.readString(file));
 }
 @Test void mergeRefusesAmbiguousOpenTabsWithoutChoosingAWinner() {
  GraphSpec a=new GraphSpec("Same",List.of(),List.of(),null,null,null,"first",List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),"line",true);
  assertThrows(IllegalArgumentException.class,()->SavedGraphMerge.merge(List.of(),List.of(a,a)),"ambiguous tabs must not silently collapse");
 }
 @Test void mergingIntoDuplicateTargetRefusesBeforeAnyCategoryChanges() throws Exception {
  var share=new SettingsShare();
  AppConfig target=new AppConfig();
  GraphSpec a=new GraphSpec("Same",List.of(),List.of(),null,null,null,"first",List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),"line",true);
  target.savedGraphs.addAll(List.of(a,a.withOpen(false)));
  var before=List.copyOf(target.savedGraphs);
  String text="share.version=1\nsourceRoot.count=1\nsourceRoot.0=new-source\ngraph.count=1\ngraph.0.name=Same\n";
  var plan=share.preview(text,target,dir);
  assertThrows(IllegalArgumentException.class,()->share.apply(plan,
    java.util.EnumSet.of(SettingsShare.Category.SOURCE_ROOTS,SettingsShare.Category.GRAPHS),target),
    "an ambiguous existing name must not choose the first definition");
  assertEquals(before,target.savedGraphs);
  assertTrue(target.sourceRoots.isEmpty(),"no other category changes before a chart refusal");
 }
}
