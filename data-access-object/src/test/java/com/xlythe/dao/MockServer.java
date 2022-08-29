package com.xlythe.dao;

import com.xlythe.dao.remote.JSONResult;
import com.xlythe.dao.remote.Server;

import org.json.JSONObject;

public class MockServer implements Server {
    private int responseCode = 500;
    private JSONResult response = null;

    @Override
    public void get(String url, JSONObject params, Callback<JSONResult> callback) {
        if (responseCode >= 200 && responseCode < 300) {
            callback.onSuccess(response);
        } else {
            callback.onFailure(new RuntimeException(response.toString()));
        }
    }

    @Override
    public void post(String url, JSONObject params, Callback<JSONResult> callback) {
        if (responseCode >= 200 && responseCode < 300) {
            callback.onSuccess(response);
        } else {
            callback.onFailure(new RuntimeException(response.toString()));
        }
    }

    @Override
    public void put(String url, JSONObject params, Callback<JSONResult> callback) {
        if (responseCode >= 200 && responseCode < 300) {
            callback.onSuccess(response);
        } else {
            callback.onFailure(new RuntimeException(response.toString()));
        }
    }

    @Override
    public void delete(String url, Callback<JSONResult> callback) {
        if (responseCode >= 200 && responseCode < 300) {
            callback.onSuccess(response);
        } else {
            callback.onFailure(new RuntimeException(response.toString()));
        }
    }

    public void setResponse(int responseCode, JSONResult response) {
        this.responseCode = responseCode;
        this.response = response;
    }
}
