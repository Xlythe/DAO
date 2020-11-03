package com.xlythe.dao.remote;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class JSONResult {
    private final String result;

    JSONResult(String result) {
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
}
