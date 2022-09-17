package com.xlythe.dao;

import android.util.Base64;

import androidx.annotation.NonNull;

public final class Param {
    private final String key;
    private final String value;
    private final String parameterizedValue;
    private final Object unformattedValue;
    private final boolean isPrimaryKey;

    public Param(String key, Object value) {
        this(key, value, /*isPrimaryKey=*/false);
    }

    public Param(String key, Object value, boolean isPrimaryKey) {
        this.key = key;
        this.unformattedValue = value;
        this.isPrimaryKey = isPrimaryKey;

        if (value == null) {
            this.value = "NULL";
            this.parameterizedValue = null;
        } else if (value instanceof String) {
            this.value = "'" + value + "'";
            this.parameterizedValue = value.toString();
        } else if (value instanceof byte[]) {
            this.value = Base64.encodeToString((byte[]) value, Base64.DEFAULT);
            this.parameterizedValue = this.value;
        } else if (value instanceof Boolean) {
            this.value = (Boolean) value ? "1" : "0";
            this.parameterizedValue = this.value;
        } else {
            this.value = value.toString();
            this.parameterizedValue = this.value;
        }
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    String getParameterizedValue() {
        return parameterizedValue;
    }

    Object getUnformattedValue() {
        return unformattedValue;
    }

    public boolean isPrimaryKey() {
        return isPrimaryKey;
    }

    @NonNull
    @Override
    public String toString() {
        return key + ":" + value;
    }
}