import telamin.fluxtion.audit.analyser.analyser.config.*;
import java.nio.file.*;
import java.util.*;
public class ProfileStyleProbe {
 public static void main(String[] args)throws Exception{
  Path dir=Files.createTempDirectory("style-import-probe-");var share=new SettingsShare();
  for(String kind:List.of("plain","external-series","external-marker"))for(String style:List.of("line","points")){
   AppConfig config=new AppConfig();
   var ext=kind.equals("external-series")?List.of(new GraphSpec.ExternalSpec("values.csv","ext","time","epochMillis",null,"value",0)):List.<GraphSpec.ExternalSpec>of();
   var markers=kind.equals("external-marker")?List.of(new GraphSpec.MarkerSpec("mark","circle",null,null,null,"events.csv","time","epochMillis",null,"value",null,0,"LOCF")):List.<GraphSpec.MarkerSpec>of();
   config.savedGraphs.add(new GraphSpec("Example",List.of(),List.of(),null,null,null,null,List.of(),List.of(),List.of(),List.of(),ext,markers,style));
   Path path=dir.resolve(kind+"-"+style+"/.analyser/project.fluxtion-settings");ProjectProfile.save(path,config,share);
   AppConfig loaded=new AppConfig();var result=ProjectProfile.load(path,loaded,share);if(!result.loaded())throw new AssertionError(result);
   var actual=loaded.savedGraphs.getFirst();System.out.println(kind+": disk="+style+", loaded="+actual.style()+", declared="+actual.declaredStyle());
  }
 }
}
