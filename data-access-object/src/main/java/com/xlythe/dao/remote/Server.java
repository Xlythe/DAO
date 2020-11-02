package com.xlythe.dao.remote;

import com.xlythe.dao.Callback;

import org.json.JSONObject;

public interface Server {
    void get(String url, JSONObject params, Callback<JSONResult> callback);
    void post(String url, JSONObject params, Callback<JSONResult> callback);
    void put(String url, JSONObject params, Callback<JSONResult> callback);
    void delete(String url, Callback<JSONResult> callback);
}
