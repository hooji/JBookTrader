package com.jbooktrader.platform.web;

import com.jbooktrader.platform.preferences.JBTPreferences;
import com.jbooktrader.platform.preferences.PreferencesHolder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link WebAuthenticator}. Tied to CODE_REVIEW.md §1.21.
 */
public class WebAuthenticatorTest {

    private String savedUser, savedPass;

    @Before
    public void saveDefaults() {
        PreferencesHolder prefs = PreferencesHolder.getInstance();
        savedUser = prefs.get(JBTPreferences.WebAccessUser);
        savedPass = prefs.get(JBTPreferences.WebAccessPassword);
    }

    @After
    public void restoreDefaults() {
        PreferencesHolder prefs = PreferencesHolder.getInstance();
        prefs.set(JBTPreferences.WebAccessUser, savedUser);
        prefs.set(JBTPreferences.WebAccessPassword, savedPass);
    }

    @Test
    public void correct_credentials_accepted() {
        PreferencesHolder.getInstance().set(JBTPreferences.WebAccessUser, "alice");
        PreferencesHolder.getInstance().set(JBTPreferences.WebAccessPassword, "secret");
        WebAuthenticator auth = new WebAuthenticator();
        assertTrue(auth.checkCredentials("alice", "secret"));
    }

    @Test
    public void wrong_password_rejected() {
        PreferencesHolder.getInstance().set(JBTPreferences.WebAccessUser, "alice");
        PreferencesHolder.getInstance().set(JBTPreferences.WebAccessPassword, "secret");
        WebAuthenticator auth = new WebAuthenticator();
        assertFalse(auth.checkCredentials("alice", "WRONG"));
    }

    @Test
    public void wrong_user_rejected() {
        PreferencesHolder.getInstance().set(JBTPreferences.WebAccessUser, "alice");
        PreferencesHolder.getInstance().set(JBTPreferences.WebAccessPassword, "secret");
        WebAuthenticator auth = new WebAuthenticator();
        assertFalse(auth.checkCredentials("bob", "secret"));
    }

    /**
     * BUG CR §1.21: authPair is `user + "/" + password`. So
     * user="alice/x" pass="y" produces the same authPair as
     * user="alice" pass="x/y". A user can authenticate as another user
     * if their credentials happen to collide on the "/" separator.
     * Expected to FAIL until the separator becomes unambiguous (or
     * username and password are compared independently).
     */
    @Test
    public void slash_separator_collision_should_be_rejected() {
        PreferencesHolder.getInstance().set(JBTPreferences.WebAccessUser, "alice");
        PreferencesHolder.getInstance().set(JBTPreferences.WebAccessPassword, "x/y");
        WebAuthenticator auth = new WebAuthenticator();
        // Wrong user, but credentials concatenate to the same authPair string.
        assertFalse("user 'alice/x' with pass 'y' should not match user 'alice' pass 'x/y'",
                auth.checkCredentials("alice/x", "y"));
    }
}
