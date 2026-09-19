package emu.grasscutter.server.http.handlers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the email shape check on the registration form.
 *
 * <p>It is the only field that is validated by pattern rather than by length, so a pattern that is
 * accidentally too loose lets anything through and one that is too strict turns real people away
 * with no way to tell why.
 */
public final class RegisterEmailPatternTest {
    private static Pattern pattern() throws Exception {
        Field field = RegisterHandler.class.getDeclaredField("EMAIL");
        field.setAccessible(true);
        return (Pattern) field.get(null);
    }

    @Test
    @DisplayName("ordinary addresses are accepted")
    public void acceptsRealAddresses() throws Exception {
        var email = pattern();
        for (var address :
                new String[] {
                    "a@b.co",
                    "someone@example.com",
                    "first.last@example.co.uk",
                    "user+tag@example.org",
                    "user_name@sub.domain.example.com"
                }) {
            assertTrue(email.matcher(address).matches(), address);
        }
    }

    @Test
    @DisplayName("things that are not addresses are rejected")
    public void rejectsNonAddresses() throws Exception {
        var email = pattern();
        for (var address :
                new String[] {
                    "",
                    "plainstring",
                    "@example.com",
                    "user@",
                    "user@example",     // no dot in the domain
                    "user@@example.com",
                    "user@.com",
                    "user name@example.com",
                    "user@exam ple.com",
                    "user@example..com"
                }) {
            assertFalse(email.matcher(address).matches(), address);
        }
    }

    @Test
    @DisplayName("a newline cannot smuggle a second line past the check")
    public void rejectsEmbeddedNewlines() throws Exception {
        // matches() anchors on its own, but only because the pattern avoids the $ that would let a
        // trailing newline through. Worth pinning rather than trusting.
        var email = pattern();
        assertFalse(email.matcher("user@example.com\n").matches());
        assertFalse(email.matcher("user@example.com\nevil@example.com").matches());
    }
}
