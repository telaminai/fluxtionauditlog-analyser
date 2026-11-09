package telamin.fluxtion.audit.analyser.analyser.account;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class SharedCredentialStoreTest {
    @TempDir Path temp;
    CompilerCredential a = new CompilerCredential("https://compiler.example.com","cred-a","acct-a","synthetic-a");
    CompilerCredential b = new CompilerCredential("https://staging.example.com","cred-b","acct-b","synthetic-b");
    private CredentialStore legacy() { return new CredentialStore() {
        CompilerCredential c = a;
        public String description() { return "test"; }
        public Optional<CompilerCredential> load() { return Optional.ofNullable(c); }
        public void save(CompilerCredential value) { c=value; }
        public void delete() { c=null; }
    }; }
    private SharedCredentialFile file() throws IOException { return new SharedCredentialFile(temp.resolve("credentials")); }
    @Test void staleLogoutAndReplacementCannotEraseNewSignIn() throws Exception {
        SharedCredentialStore first = new SharedCredentialStore(file(),legacy());
        SharedCredentialStore second = new SharedCredentialStore(file(),legacy());
        assertTrue(first.replace(null,a)); assertTrue(second.replace(a,b));
        assertFalse(first.delete(a)); assertFalse(first.replace(a,a));
        assertEquals(b,first.load().orElseThrow()); assertTrue(second.delete(b));
        assertTrue(first.load().isEmpty());
    }
    @Test void migrationRequiresChoiceAndDoesNotResurrectAfterLogout() throws Exception {
        CredentialStore old = legacy(); SharedCredentialStore store = new SharedCredentialStore(file(),old);
        assertTrue(store.load().isEmpty());
        store.save(b); SharedCredentialStore.Migration m = store.migration().orElseThrow();
        assertEquals(a,m.previous()); assertEquals(b,m.shared());
        store.migrate(m,false); assertEquals(b,store.load().orElseThrow()); assertTrue(old.load().isEmpty());
        store.delete(b); old.save(a); // a stale older client cannot silently reactivate the OS entry
        assertTrue(store.migration().isEmpty()); assertTrue(store.load().isEmpty());
    }
    @Test void externalChangeDuringMigrationIsRetained() throws Exception {
        SharedCredentialStore store = new SharedCredentialStore(file(),legacy());
        SharedCredentialStore.Migration observed = store.migration().orElseThrow();
        store.save(b); assertThrows(IOException.class,()->store.migrate(observed,true));
        assertEquals(b,store.load().orElseThrow());
    }
    @Test void linksIncludingDanglingParentsAreRefusedForReadWriteAndDelete() throws Exception {
        Path real=temp; Path target=Files.createDirectory(real.resolve("target"));
        Path link=real.resolve("link"); Files.createSymbolicLink(link,target);
        SharedCredentialFile file=new SharedCredentialFile(link.resolve("credentials"),false,real);
        assertThrows(IOException.class,file::read);
        assertThrows(IOException.class,()->file.write(new SharedCredentialFile.Stored("synthetic","id","account","https://compiler.example.com")));
        assertThrows(IOException.class,file::delete);
        Files.delete(link); Files.createSymbolicLink(link,real.resolve("absent"));
        assertThrows(IOException.class,file::read); assertThrows(IOException.class,file::delete);
    }
    @Test void migrationMarkerKeepsTheCredentialDirectoryBoundaryWithAliasedHome() throws Exception {
        Path home=Files.createDirectory(temp.resolve("home")), outside=Files.createDirectory(temp.resolve("outside"));
        Path alias=temp.resolve("alias");Files.createSymbolicLink(alias,home);
        String previousHome=System.getProperty("user.home"), override=System.getProperty(SharedCredentialFile.FILE_PROPERTY);
        try {
            System.setProperty("user.home",alias.toString());System.clearProperty(SharedCredentialFile.FILE_PROPERTY);
            SharedCredentialFile file=SharedCredentialFile.defaultStore();
            new SharedCredentialFile(outside.resolve("credentials.os-migrated")).write(
                    new SharedCredentialFile.Stored("complete",null,null,null));
            Files.createSymbolicLink(home.resolve(".fluxtion"),outside);
            assertThrows(IOException.class,file::read);
            SharedCredentialStore store=new SharedCredentialStore(file,legacy());
            assertThrows(IOException.class,store::migration,"the marker must not trust a link the credential refuses");
        } finally {
            System.setProperty("user.home",previousHome);
            if(override==null)System.clearProperty(SharedCredentialFile.FILE_PROPERTY);else System.setProperty(SharedCredentialFile.FILE_PROPERTY,override);
        }
    }

}
