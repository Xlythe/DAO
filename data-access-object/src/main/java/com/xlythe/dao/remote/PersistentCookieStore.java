package com.xlythe.dao.remote;

import static com.xlythe.dao.remote.DefaultServer.TAG;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PersistentCookieStore implements CookieStore {
    private static final String FILE_NAME = "PersistentCookieStore";

    private final SharedPreferences sharedPreferences;

    public PersistentCookieStore(Context context) {
        sharedPreferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    @Override
    public List<HttpCookie> get(URI uri) {
        boolean hasExpiredCookies = false;
        List<HttpCookie> cookies = new ArrayList<>();
        for (HttpCookie cookie : decode(sharedPreferences.getStringSet(uri.getHost(), new HashSet<>()))) {
            if (cookie.hasExpired()) {
                hasExpiredCookies = true;
                continue;
            }
            cookies.add(cookie);
        }
        if (hasExpiredCookies) {
            store(uri.getHost(), cookies);
        }
        return cookies;
    }

    @Override
    public List<URI> getURIs() {
        List<URI> uris = new ArrayList<>();
        for (String key : sharedPreferences.getAll().keySet()) {
            try {
                uris.add(new URI("http", null, key, -1, null, null, null));
            } catch (URISyntaxException e) {
                Log.w(TAG, "Failed to decode uri " + key, e);
            }
        }
        return uris;
    }

    @Override
    public List<HttpCookie> getCookies() {
        List<HttpCookie> allCookies = new ArrayList<>();
        for (String key : sharedPreferences.getAll().keySet()) {
            boolean hasExpiredCookies = false;
            List<HttpCookie> cookies = new ArrayList<>();
            for (HttpCookie cookie : decode(sharedPreferences.getStringSet(key, new HashSet<>()))) {
                if (cookie.hasExpired()) {
                    hasExpiredCookies = true;
                    continue;
                }
                cookies.add(cookie);
            }
            if (hasExpiredCookies) {
                store(key, cookies);
            }
            allCookies.addAll(cookies);
        }
        return allCookies;
    }

    @Override
    public void add(URI uri, HttpCookie cookie) {
        Collection<HttpCookie> cookies = get(uri);
        cookies.remove(cookie);
        if (!cookie.hasExpired()) cookies.add(cookie);
        store(uri.getHost(), cookies);
    }

    @Override
    public boolean remove(URI uri, HttpCookie cookie) {
        Collection<HttpCookie> cookies = get(uri);

        // HttpCookie matches on Name, Domain, and Path.
        if (!cookies.remove(cookie)) {
            return false;
        }

        store(uri.getHost(), cookies);
        return true;
    }

    @Override
    public boolean removeAll() {
        boolean hadCookies = !sharedPreferences.getAll().isEmpty();
        sharedPreferences.edit().clear().apply();
        return hadCookies;
    }

    private void store(String host, Collection<HttpCookie> cookies) {
        SharedPreferences.Editor edit = sharedPreferences.edit();
        if (cookies.isEmpty()) edit.remove(host);
        else edit.putStringSet(host, encode(cookies));
        edit.apply();
    }

    private static Set<String> encode(Collection<HttpCookie> cookies) {
        Set<String> encodedCookies = new HashSet<>();
        for (HttpCookie cookie : cookies) {
            try {
                encodedCookies.add(encode(new SerializableHttpCookie(cookie)));
            } catch (JSONException e) {
                Log.w(TAG, "Failed to encode HttpCookie " + cookie.getName(), e);
            }
        }
        return encodedCookies;
    }

    private static String encode(SerializableHttpCookie cookie) throws JSONException {
        return cookie.asJson().toString();
    }

    private static Set<HttpCookie> decode(Collection<String> encodedCookies) {
        Set<HttpCookie> cookies = new HashSet<>();
        for (String encodedCookie : encodedCookies) {
            try {
                cookies.add(decode(encodedCookie));
            } catch (JSONException e) {
                Log.w(TAG, "Failed to decode HttpCookie " + encodedCookie, e);
            }
        }
        return cookies;
    }

    private static HttpCookie decode(String encodedString) throws JSONException {
        return new SerializableHttpCookie(new JSONObject(encodedString)).getCookie();
    }
}