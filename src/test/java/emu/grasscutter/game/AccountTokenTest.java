package emu.grasscutter.game;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Token validation must reject absent and mismatched credentials at each login stage. */
public class AccountTokenTest {
    @Test
    public void missingCredentialsNeverAuthenticate() {
        var account = new Account();

        assertFalse(account.matchesLoginToken(null));
        assertFalse(account.matchesLoginToken(""));
        assertFalse(account.matchesSessionKey(null));
        assertFalse(account.matchesSessionKey(""));
    }

    @Test
    public void loginAndSessionCredentialsAreNotInterchangeable() {
        var account = new Account();
        account.setToken("game-token");
        account.setSessionKey("v2_account-session");

        assertTrue(account.matchesLoginToken("game-token"));
        assertFalse(account.matchesLoginToken("v2_account-session"));
        assertFalse(account.matchesLoginToken("wrong-token"));

        assertTrue(account.matchesSessionKey("v2_account-session"));
        assertFalse(account.matchesSessionKey("game-token"));
        assertFalse(account.matchesSessionKey("wrong-session"));
    }

    @Test
    public void blankStoredCredentialsNeverAuthenticate() {
        var account = new Account();
        account.setToken("");
        account.setSessionKey("");

        assertFalse(account.matchesLoginToken(""));
        assertFalse(account.matchesSessionKey(""));
    }
}
