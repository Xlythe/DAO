package com.xlythe.dao;

import android.util.Base64;

public final class Param {
    private final String key;
    private final String value;
    private final Object unformattedValue;

    public Param(String key, Object value) {
        this.key = key;
        this.unformattedValue = value;

        if (value == null) {
            this.value = "NULL";
        } else if (value instanceof String) {
            this.value = "'" + value + "'";
        } else if (value instanceof byte[]) {
            this.value = Base64.encodeToString((byte[]) value, Base64.DEFAULT);
        } else if (value instanceof Boolean) {
            this.value = (Boolean) value ? "1" : "0";
        } else {
            this.value = value.toString();
        }
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    Object getUnformattedValue() {
        return unformattedValue;
    }

    @Override
    public String toString() {
        return key + ":" + value;
    }
}