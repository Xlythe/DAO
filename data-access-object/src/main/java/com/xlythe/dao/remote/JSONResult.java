package com.xlythe.dao.remote;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class JSONResult {
    private final String result;

    public JSONResult(String result) {
        this.result = result;
    }

    public JSONObject asJSONObject() {
        try {
            return new JSONObject(result);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    public JSONArray asJSONArray() {
        try {
            return new JSONArray(result);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean isJSONObject() {
        try {
            asJSONObject();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public boolean isJSONArray() {
        try {
            asJSONArray();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }
}
