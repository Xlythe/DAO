package com.xlythe.dao.remote;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.JsonRequest;
import com.android.volley.toolbox.Volley;
import com.xlythe.dao.Callback;
import com.xlythe.dao.Model;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.CookieStore;
import java.util.Iterator;

public class DefaultServer implements Server {
    static final String TAG = Server.class.getSimpleName();
    static final boolean DEBUG = false;

    private final RequestQueue mRequestQueue;

    public DefaultServer(Context context) {
        mRequestQueue = Volley.newRequestQueue(context.getApplicationContext());

        CookieStore cookieStore = new PersistentCookieStore(context.getApplicationContext());
        CookieManager manager = new CookieManager(cookieStore, CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(manager);
    }

    @Override
    public void get(String url, JSONObject params, Callback<JSONResult> callback) {
        if (DEBUG) {
            Log.d(TAG, "get=" + url + ", params=" + params);
        }

        StringBuilder encodedUrl = new StringBuilder(url);
        for (Iterator<String> it = params.keys(); it.hasNext();) {
            String key = it.next();
            encodedUrl.append(encodedUrl.toString().contains("?") ? "&" : "?");
            try {
                encodedUrl.append(key).append("=").append(params.get(key));
            } catch (JSONException e) {
                // Should not happen
                throw new RuntimeException(e);
            }
        }

        mRequestQueue.add(new JsonRequest(Request.Method.GET, encodedUrl.toString(), callback::onSuccess, callback::onFailure));
    }

    @Override
    public void post(String url, JSONObject params, Callback<JSONResult> callback) {
        if (DEBUG) {
            Log.d(TAG, "post=" + url + ", params=" + params);
        }

        mRequestQueue.add(new JsonRequest(Request.Method.POST, url, params, callback::onSuccess, callback::onFailure));
    }

    @Override
    public void put(String url, JSONObject params, Callback<JSONResult> callback) {
        if (DEBUG) {
            Log.d(TAG, "put=" + url + ", params=" + params);
        }

        mRequestQueue.add(new JsonRequest(Request.Method.PUT, url, params, callback::onSuccess, callback::onFailure));
    }

    @Override
    public void delete(String url, Callback<JSONResult> callback) {
        if (DEBUG) {
            Log.d(TAG, "delete=" + url);
        }

        mRequestQueue.add(new JsonRequest(Request.Method.DELETE, url, callback::onSuccess, callback::onFailure));
    }

    private static class JsonRequest extends Request<JSONResult> {
        /** Default charset for JSON request. */
        protected static final String PROTOCOL_CHARSET = "utf-8";

        /** Content type for request. */
        private static final String PROTOCOL_CONTENT_TYPE =
                String.format("application/json; charset=%s", PROTOCOL_CHARSET);

        private Response.Listener<JSONResult> mListener;

        private final String mRequestBody;

        public JsonRequest(int method, String url, Response.Listener<JSONResult> listener, Response.ErrorListener errorListener) {
            this(method, url, null, listener, errorListener);
        }

        public JsonRequest(int method, String url, JSONObject requestBody, Response.Listener<JSONResult> listener, Response.ErrorListener errorListener) {
            super(method, url, errorListener);
            mListener = listener;
            mRequestBody = requestBody.toString();
        }

        @Override
        public void cancel() {
            super.cancel();
            synchronized (this) {
                mListener = null;
            }
        }

        @Override
        protected void deliverResponse(JSONResult response) {
            Response.Listener<JSONResult> listener;
            synchronized (this) {
                listener = mListener;
            }
            if (listener != null) {
                listener.onResponse(response);
            }
        }

        @Override
        protected Response<JSONResult> parseNetworkResponse(NetworkResponse response) {
            if (response.statusCode < 200 || response.statusCode >= 300) {
                return Response.error(new ParseError(response));
            }

            try {
                String jsonString =
                        new String(
                                response.data,
                                HttpHeaderParser.parseCharset(response.headers, PROTOCOL_CHARSET));
                return Response.success(new JSONResult(jsonString), HttpHeaderParser.parseCacheHeaders(response));
            } catch (UnsupportedEncodingException e) {
                return Response.error(new ParseError(e));
            }
        }

        @Override
        public String getBodyContentType() {
            return PROTOCOL_CONTENT_TYPE;
        }

        @Nullable
        @Override
        public byte[] getBody() {
            try {
                return mRequestBody == null ? null : mRequestBody.getBytes(PROTOCOL_CHARSET);
            } catch (UnsupportedEncodingException uee) {
                VolleyLog.wtf(
                        "Unsupported Encoding while trying to get the bytes of %s using %s",
                        mRequestBody, PROTOCOL_CHARSET);
                return null;
            }
        }
    }
}
