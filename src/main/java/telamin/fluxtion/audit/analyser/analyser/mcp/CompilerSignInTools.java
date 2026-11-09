package telamin.fluxtion.audit.analyser.analyser.mcp;

import java.util.*;

/** Authentication belongs to the stdio client's session, not to the loaded audit-log action socket. */
final class CompilerSignInTools {
    static final String START="compiler_sign_in_start", STATUS="compiler_sign_in_status", CANCEL="compiler_sign_in_cancel";
    static boolean owns(Object name){return START.equals(name)||STATUS.equals(name)||CANCEL.equals(name);}
    static List<Map<String,Object>> list(){
        List<Map<String,Object>> out=new ArrayList<>();
        for(String name:List.of(START,STATUS,CANCEL)) {
            boolean start=START.equals(name),status=STATUS.equals(name);
            Map<String,Object> properties=new LinkedHashMap<>();
            properties.put(start?"compilerOrigin":"attemptId",Map.of("type","string","maxLength",start?2048:128));
            if(start)properties.put("confirmReplaceService",Map.of("type","boolean","description","True only after the user explicitly confirms replacing and revoking the previous machine sign-in."));
            String description=start?"Start compiler sign-in only after the user asks to sign in or build. Relay the returned verification address and user code, then poll this attempt. Never read credentials. Service replacement requires explicit user confirmation."
                    :status?"Read only this session's sign-in attempt. A saved, verified account does not prove an external build's effective account."
                    :"Cancel only this session's pending sign-in attempt; does not log out or delete an existing credential.";
            out.add(Map.of("name",name,"description",description,"inputSchema",Map.of("type","object","properties",properties,
                    "required",List.of(start?"compilerOrigin":"attemptId"),"additionalProperties",false),
                    "annotations",Map.of("readOnlyHint",status,"destructiveHint",start,"idempotentHint",!start,"openWorldHint",true)));
        }
        return out;
    }
}
