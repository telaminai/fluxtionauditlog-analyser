import telamin.fluxtion.audit.analyser.analyser.ui.*;
import telamin.fluxtion.audit.analyser.analyser.config.*;
import telamin.fluxtion.audit.analyser.analyser.parse.*;
import telamin.fluxtion.audit.analyser.analyser.filter.*;
import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.util.List;
import java.util.*;

/** Review probe: actual JComboBox callbacks, restore suppression, and public share/profile routes. */
public class StyleContractProbe {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static JComboBox<?> combo(Container p){for(Component c:p.getComponents()){if(c instanceof JComboBox<?> b&&b.getItemCount()==3&&"Stairs".equals(b.getItemAt(0)))return b;if(c instanceof Container k){var b=combo(k);if(b!=null)return b;}}return null;}
 static void callbacks(String name)throws Exception {
  SwingUtilities.invokeAndWait(()->{
   GraphPanel panel=new GraphPanel();int[] edits={0};panel.setOnMutation(()->edits[0]++);
   for(String style:List.of("step","step","line","line","points","points")){int before=edits[0];panel.setStyleByName(style);check(edits[0]==before+1,name+" setter expected one callback for "+style+", got "+(edits[0]-before));check(panel.styleName().equals(style),"style follows setter");}
   for(int i:List.of(0,0,1,1,2,2)){int before=edits[0];combo(panel).setSelectedIndex(i);check(edits[0]==before+1,name+" combo expected one callback");}
   GraphTabs tabs=new GraphTabs();int[] saves={0};tabs.setChangeListener(()->saves[0]++);tabs.bind(new HeapLogStore(""),new FilterState());
   tabs.restore(List.of(spec("line",true,"plain"),spec("points",true,"plain")));
   check(saves[0]==0,name+" restore wrote back mid-rebuild");check(tabs.specs().stream().map(GraphSpec::style).toList().equals(List.of("line","points")),"restored styles");
   tabs.graphNamed("line-true-plain").setStyleByName("line");check(saves[0]==1,"restoring flag must clear after restore");tabs.unbind();
   System.out.println("PASS "+name+": 12 single callbacks, restore zero writes, post-restore edit one write");
  });
 }
 static GraphSpec spec(String style,boolean open,String kind){return new GraphSpec(style+"-"+open+"-"+kind,List.of("node\u0001value"),List.of(new GraphSpec.ExprSpec("derived","1+2","LOCF")),1000L,2000L,"caption","explanation",List.of(new GraphSpec.NoteSpec(1000,"note",null)),List.of("node.value"),List.of(new GraphSpec.GuideSpec(3,"guide",false)),List.of(new GraphSpec.BandSpec("1==1","band")),kind.equals("series")?List.of(new GraphSpec.ExternalSpec("values.csv","external","time","epochMillis","UTC","value",0)):List.of(),kind.equals("marker")?List.of(new GraphSpec.MarkerSpec("marker","circle",null,null,null,"events.csv","time","epochMillis","UTC","value",null,0,"LOCF")):List.of(),style,open);}
 public static void main(String[]args)throws Exception{
  if(args.length==0||!args[0].equals("paths-only")){
   for(String theme:ThemeManager.THEMES){SwingUtilities.invokeAndWait(()->ThemeManager.apply(theme));callbacks("FlatLaf "+theme);}
   for(var laf:UIManager.getInstalledLookAndFeels()){
    try{SwingUtilities.invokeAndWait(()->{try{UIManager.setLookAndFeel(laf.getClassName());}catch(Exception e){throw new RuntimeException(e);}});}catch(Exception e){System.out.println("UNSUPPORTED "+laf.getName()+": "+e.getCause());continue;}callbacks(laf.getName());
   }
  }
  Path root=Files.createTempDirectory("style-contract-review-");var share=new SettingsShare(root.toString());int cases=0;
  for(String kind:List.of("plain","series","marker"))for(String style:List.of("line","points"))for(boolean open:List.of(true,false)){
   var original=spec(style,open,kind);var config=new AppConfig();config.savedGraphs.add(original);
   Path profile=ProjectProfile.pathFor(root.resolve(original.name()));ProjectProfile.save(profile,config,share);var loaded=new AppConfig();check(ProjectProfile.load(profile,loaded,share).loaded(),"profile load");var actual=loaded.savedGraphs.getFirst();
   check(actual.style().equals(style)&&actual.open()==open,"PROFILE_METADATA "+original.name()+": got "+actual.style()+"/"+actual.open());
   check(actual.withExternal(original.external(),original.markers()).equals(original),"profile changed non-path components");
   if(kind.equals("series"))check(Path.of(actual.external().getFirst().path()).isAbsolute(),"series path resolved");if(kind.equals("marker"))check(Path.of(actual.markers().getFirst().extPath()).isAbsolute(),"marker path resolved");
   var copy=new AppConfig();var plan=share.preview(share.export(loaded,Set.of(SettingsShare.Category.GRAPHS)),copy,root);share.apply(plan,Set.of(SettingsShare.Category.GRAPHS),copy);check(copy.savedGraphs.getFirst().equals(actual),"SHARE_METADATA "+original.name());cases++;
  }
  System.out.println("PASS profile save/load and share export/preview/apply: "+cases+" cases, all components retained except resolved paths");
 }
}
