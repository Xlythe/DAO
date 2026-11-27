package com.xlythe.dao.remote;

import android.net.Uri;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.net.HttpCookie;
import java.net.URI;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {24, 37})
public class TransportTest {
    @Test public void queryParametersRoundTripSpecialCharactersAndPreserveTheOriginalUrl() throws Exception {
        String value = "a & b=+/東京";
        Uri url = Uri.parse(DefaultServer.queryUrl("https://example.org/path?old=1#section",
                new JSONObject().put("name & title", value)));
        assertEquals(value, url.getQueryParameter("name & title"));
        assertEquals("1", url.getQueryParameter("old"));
        assertEquals("section", url.getFragment());
        assertEquals(2, url.getQueryParameterNames().size());
    }

    @Test public void cookieReplacementAndExpirySurviveReopeningTheStore() {
        URI host = URI.create("https://example.org/game");
        PersistentCookieStore store = new PersistentCookieStore(RuntimeEnvironment.getApplication());
        HttpCookie first = new HttpCookie("session", "old");
        HttpCookie second = new HttpCookie("session", "new");
        store.add(host, first);
        store.add(host, second);
        store = new PersistentCookieStore(RuntimeEnvironment.getApplication());
        assertEquals(1, store.get(host).size());
        assertEquals("new", store.get(host).get(0).getValue());
        assertEquals(host.getHost(), store.getURIs().get(0).getHost());
        second.setMaxAge(0);
        store.add(host, second);
        assertTrue(store.get(host).isEmpty());
        assertTrue(store.getURIs().isEmpty());
    }

    @Test public void removingACookieDoesNotAffectAnotherHost() {
        PersistentCookieStore store = new PersistentCookieStore(RuntimeEnvironment.getApplication());
        URI first = URI.create("https://one.example");
        URI second = URI.create("https://two.example");
        HttpCookie cookie = new HttpCookie("session", "value");
        store.add(first, cookie);
        store.add(second, cookie);
        assertTrue(store.remove(first, cookie));
        assertFalse(store.remove(first, cookie));
        assertEquals(1, store.getCookies().size());
        assertTrue(store.removeAll());
        assertFalse(store.removeAll());
    }
}
