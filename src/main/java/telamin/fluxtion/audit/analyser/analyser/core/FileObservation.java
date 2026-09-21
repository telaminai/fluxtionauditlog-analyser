package telamin.fluxtion.audit.analyser.analyser.core;

import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/** Cheap change detection, explicitly not proof of unchanged content. No automatic reload. */
public final class FileObservation {
    private FileObservation() { }
    public static Map<String,Object> capture(Path path) {
        var out=new LinkedHashMap<String,Object>();
        out.put("path",path.toAbsolutePath().normalize().toString());
        try {
            var a=Files.readAttributes(path,BasicFileAttributes.class);
            out.put("sizeBytes",a.size());out.put("modified",a.lastModifiedTime().toString());
            out.put("fileKey",String.valueOf(a.fileKey()));out.put("directory",a.isDirectory());
        } catch(java.nio.file.NoSuchFileException e) {out.put("problem","missing");}
        catch(java.io.IOException | SecurityException e) {out.put("problem","unavailable");}
        return Collections.unmodifiableMap(out);
    }
    public static Map<String,Object> compare(List<Map<String,Object>> loaded) {
        if(loaded.isEmpty()) return Map.of("state","unknown","basis","no read-time metadata observation");
        var members=new ArrayList<Map<String,Object>>();String state="unchanged-metadata";
        for(var before:loaded) {
            var now=capture(Path.of(before.get("path").toString()));
            String one=now.containsKey("problem") ? now.get("problem").toString()
                    : before.containsKey("problem") ? "unknown"
                    : before.equals(now) ? "unchanged-metadata" : "changed-on-disk";
            if(!one.equals("unchanged-metadata")) state="changed-on-disk";
            members.add(Map.of("loaded",before,"onDisk",now,"state",one));
        }
        return Map.of("state",state,"basis","size, modification time and file identity; unchanged metadata does not prove identical bytes",
                "members",members,"reload","explicitly reopen the same path; no automatic replacement");
    }
    @SuppressWarnings("unchecked")
    public static List<Map<String,Object>> observations(Object value) {
        if(!(value instanceof List<?> list))return List.of();
        return list.stream().filter(Map.class::isInstance).map(o->(Map<String,Object>)o).toList();
    }
}
