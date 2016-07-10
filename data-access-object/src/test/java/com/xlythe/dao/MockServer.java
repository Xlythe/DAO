package com.xlythe.dao;

import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.RequestParams;

public class MockServer implements RemoteModel.Server {
    private int responseCode = 500;
    private String response = null;

    @Override
    public void get(String url, RequestParams params, JsonHttpResponseHandler responseHandler) {
        if (responseCode >= 200 && responseCode < 300) {
            responseHandler.onSuccess(responseCode, null, response);
        } else {
            responseHandler.onFailure(responseCode, null, response, new RuntimeException(response));
        }
    }

    @Override
    public void post(String url, String json, JsonHttpResponseHandler responseHandler) {
        if (responseCode >= 200 && responseCode < 300) {
            responseHandler.onSuccess(responseCode, null, response);
        } else {
            responseHandler.onFailure(responseCode, null, response, new RuntimeException(response));
        }
    }

    @Override
    public void put(String url, RequestParams params, JsonHttpResponseHandler responseHandler) {
        if (responseCode >= 200 && responseCode < 300) {
            responseHandler.onSuccess(responseCode, null, response);
        } else {
            responseHandler.onFailure(responseCode, null, response, new RuntimeException(response));
        }
    }

    @Override
    public void delete(String url, JsonHttpResponseHandler responseHandler) {
        if (responseCode >= 200 && responseCode < 300) {
            responseHandler.onSuccess(responseCode, null, response);
        } else {
            responseHandler.onFailure(responseCode, null, response, new RuntimeException(response));
        }
    }

    public void setResponse(int responseCode, String response) {
        this.responseCode = responseCode;
        this.response = response;
    }
}
