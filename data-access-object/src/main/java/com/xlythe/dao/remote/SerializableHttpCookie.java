package com.xlythe.dao.remote;

import android.os.Build;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.HttpCookie;

public class SerializableHttpCookie {
    private final static long MAX_AGE_UNSPECIFIED = -1;

    private final HttpCookie cookie;

    // HttpCookie doesn't expose a way to modify the timestamp (epoch) when it was created. As a
    // workaround, we'll track this separately. Max age, if set, will be modified instead when
    // deserializing.
    private final long whenCreated;

    public SerializableHttpCookie(HttpCookie cookie) {
        this.cookie = cookie;
        this.whenCreated = System.currentTimeMillis();
    }

    public SerializableHttpCookie(JSONObject jsonObject) throws JSONException {
        this.cookie = fromJson(jsonObject);
        this.whenCreated = System.currentTimeMillis();
    }

    private static HttpCookie fromJson(JSONObject jsonObject) throws JSONException {
        String name = jsonObject.getString("name");
        String value = jsonObject.getString("value");
        HttpCookie cookie = new HttpCookie(name, value);

        if (jsonObject.has("comment")) {
            cookie.setComment(jsonObject.getString("comment"));
        }
        if (jsonObject.has("comment_url")) {
            cookie.setCommentURL(jsonObject.getString("comment_url"));
        }
        if (jsonObject.has("discard")) {
            cookie.setDiscard(jsonObject.getBoolean("discard"));
        }
        if (jsonObject.has("domain")) {
            cookie.setDomain(jsonObject.getString("domain"));
        }
        if (jsonObject.has("http_only") && Build.VERSION.SDK_INT >= 24) {
            cookie.setHttpOnly(jsonObject.getBoolean("http_only"));
        }
        if (jsonObject.has("max_age")) {
            long maxAge = jsonObject.getLong("max_age");
            if (maxAge == MAX_AGE_UNSPECIFIED) {
                cookie.setMaxAge(maxAge);
            } else {
                // Time doesn't move while the cookie is serialized. Since we're unable to set
                // whenCreated on HttpCookie, we'll adjust maxAge as necessary.
                long whenCreated = jsonObject.has("when_created") ? jsonObject.getLong("when_created") : 0;
                long timeElapsed = (System.currentTimeMillis() - whenCreated) / 1000;
                cookie.setMaxAge(Math.max(0, maxAge - timeElapsed));
            }
        }
        if (jsonObject.has("path")) {
            cookie.setPath(jsonObject.getString("path"));
        }
        if (jsonObject.has("portlist")) {
            cookie.setPortlist(jsonObject.getString("portlist"));
        }
        if (jsonObject.has("secure")) {
            cookie.setSecure(jsonObject.getBoolean("secure"));
        }
        if (jsonObject.has("version")) {
            cookie.setVersion(jsonObject.getInt("version"));
        }

        return cookie;
    }

    public HttpCookie getCookie() {
        return cookie;
    }

    public JSONObject asJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("name", cookie.getName());
        jsonObject.put("value", cookie.getValue());
        jsonObject.put("comment", cookie.getComment());
        jsonObject.put("comment_url", cookie.getCommentURL());
        jsonObject.put("discard", cookie.getDiscard());
        jsonObject.put("domain", cookie.getDomain());
        if (Build.VERSION.SDK_INT >= 24) {
            jsonObject.put("http_only", cookie.isHttpOnly());
        }
        jsonObject.put("max_age", cookie.getMaxAge());
        jsonObject.put("path", cookie.getPath());
        jsonObject.put("portlist", cookie.getPortlist());
        jsonObject.put("secure", cookie.getSecure());
        jsonObject.put("version", cookie.getVersion());
        jsonObject.put("when_created", whenCreated);
        return jsonObject;
    }
}
