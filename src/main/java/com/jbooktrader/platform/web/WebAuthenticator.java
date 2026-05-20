package com.jbooktrader.platform.web;

import com.jbooktrader.platform.preferences.PreferencesHolder;
import com.jbooktrader.platform.startup.JBookTrader;
import com.sun.net.httpserver.BasicAuthenticator;

import static com.jbooktrader.platform.preferences.JBTPreferences.WebAccessPassword;
import static com.jbooktrader.platform.preferences.JBTPreferences.WebAccessUser;

/**
 * @author Eugene Kononov
 */
public class WebAuthenticator extends BasicAuthenticator {
    private final String expectedUser;
    private final String expectedPassword;

    public WebAuthenticator() {
        super(JBookTrader.APP_NAME);
        PreferencesHolder prefs = PreferencesHolder.getInstance();
        expectedUser = prefs.get(WebAccessUser);
        expectedPassword = prefs.get(WebAccessPassword);
    }

    @Override
    public boolean checkCredentials(String userName, String password) {
        return expectedUser.equals(userName) && expectedPassword.equals(password);
    }
}