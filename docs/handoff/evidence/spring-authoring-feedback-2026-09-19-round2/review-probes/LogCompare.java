import java.nio.file.*;
import java.util.*;
import telamin.fluxtion.audit.analyser.analyser.parse.*;
import telamin.fluxtion.audit.analyser.analyser.model.*;
public class LogCompare {
 static String norm(LogRecord r){
  return r.rawText().replaceAll("(?m)^[ \\t]*(logTime|eventTime|endTime):[^\\n]*(?:\\n|$)", "")
      .replaceAll("thread: [^,}\\n]+", "thread: NORMALIZED").trim();
 }
 static List<String> business(HeapLogStore s){var out=new ArrayList<String>();for(int i=0;i<s.size();i++){var r=s.record(i);if(Set.of("Trade","PriceUpdate").contains(r.event()))out.add(norm(r));}return out;}
 public static void main(String[] a)throws Exception{
  var t=HeapLogStore.fromFile(Path.of(a[0]));var x=HeapLogStore.fromFile(Path.of(a[1]));var y=HeapLogStore.fromFile(Path.of(a[2]));
  System.out.println("Business records="+business(t).size()+", standalone vs host1="+business(t).equals(business(x))+", host1 vs host2="+business(x).equals(business(y)));
  for(int i=0;i<business(t).size();i++)if(!business(t).get(i).equals(business(x).get(i))){System.out.println("FIRST BUSINESS DIFFERENCE:\n"+business(t).get(i)+"\nVERSUS\n"+business(x).get(i));break;}
  for(int i=0;i<x.size();i++)if(!norm(x.record(i)).equals(norm(y.record(i))))System.out.println("Different full-log row="+i+" event="+x.record(i).event()+"\n  first="+x.record(i).eventToString()+"\n  second="+y.record(i).eventToString());
 }
}
