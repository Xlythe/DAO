package com.xlythe.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Base64;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;

import static com.xlythe.dao.Util.*;
import static com.xlythe.dao.Model.TAG;

public class Transcriber {

    /**
     * Updates the values of the instance with the cursor's current position
     */
    static <B extends BaseModel> BaseModel<B> inflate(BaseModel<B> instance, Cursor cursor) {
        try {
            for (Field field : instance.getFields()) {
                if (isInt(field)) {
                    field.setInt(instance, cursor.getInt(cursor.getColumnIndex(field.getName())));
                } else if (isLong(field)) {
                    field.setLong(instance, cursor.getLong(cursor.getColumnIndex(field.getName())));
                } else if (isFloat(field)) {
                    field.setFloat(instance, cursor.getFloat(cursor.getColumnIndex(field.getName())));
                } else if (isBoolean(field)) {
                    field.setBoolean(instance, 1 == cursor.getInt(cursor.getColumnIndex(field.getName())));
                } else if (isString(field)) {
                    field.set(instance, cursor.getString(cursor.getColumnIndex(field.getName())));
                } else if (isByteArray(field)) {
                    field.set(instance, cursor.getBlob(cursor.getColumnIndex(field.getName())));
                } else {
                    throw new UnsupportedClassVersionError(field.getType() + " is not supported");
                }
            }
        } catch (IllegalAccessException e) {
            Log.e(TAG, "Access wasn't public. Should never happen, though.", e);
        }
        return instance;
    }

    /**
     * Updates the values of the instance with the JSONObject's values
     */
    static <A extends BaseModel<A>> A inflate(A instance, JSONObject object) {
        try {
            for (Field field : instance.getFields()) {
                // Special case for _ID, which is a field we add ourselves.
                if (BaseModel._ID.equals(field.getName()) && !object.has(BaseModel._ID)) {
                    continue;
                }
                if (isInt(field)) {
                    field.setInt(instance, object.getInt(field.getName()));
                } else if (isLong(field)) {
                    field.setLong(instance, object.getLong(field.getName()));
                } else if (isFloat(field)) {
                    field.setFloat(instance, (float) object.getDouble(field.getName()));
                } else if (isBoolean(field)) {
                    field.setBoolean(instance, object.getBoolean(field.getName()));
                } else if (isString(field)) {
                    field.set(instance, object.getString(field.getName()));
                } else if (isByteArray(field)) {
                    byte[] bytes = Base64.decode(object.getString(field.getName()), Base64.DEFAULT);
                    field.set(instance, bytes);
                } else {
                    throw new UnsupportedClassVersionError(field.getType() + " is not supported");
                }
            }
        } catch (IllegalAccessException e) {
            Log.e(TAG, "Access wasn't public. Should never happen, though.", e);
        } catch (JSONException e) {
            Log.e(TAG, "Trouble parsing the json object", e);
        }
        return instance;
    }

    /**
     * Updates the values of the instance with the values from the params
     */
    static <A extends BaseModel<A>> A inflate(A instance, Param... params) {
        try {
            for (Param param : params) {
                Field field = instance.getField(param.getKey());
                if (isInt(field)) {
                    field.setInt(instance, Integer.parseInt(param.getValue()));
                } else if (isLong(field)) {
                    field.setLong(instance, Long.parseLong(param.getValue()));
                } else if (isFloat(field)) {
                    field.setFloat(instance, Float.parseFloat(param.getValue()));
                } else if (isBoolean(field)) {
                    field.setBoolean(instance, Boolean.parseBoolean(param.getUnformattedValue().toString()));
                } else if (isString(field)) {
                    field.set(instance, param.getUnformattedValue());
                } else if (isByteArray(field)) {
                    field.set(instance, Base64.decode(param.getValue(), Base64.DEFAULT));
                } else {
                    throw new UnsupportedClassVersionError(field.getType() + " is not supported");
                }
            }
        } catch (IllegalAccessException e) {
            Log.e(TAG, "Access wasn't public. Should never happen, though.", e);
        } catch (NoSuchFieldException e) {
            Log.e(TAG, "Trouble parsing the parameters", e);
        }
        return instance;
    }

    /**
     * Creates a ContentValues from the given model
     */
    static <A extends BaseModel<A>> ContentValues getContentValues(A instance) {
        ContentValues contentValues = new ContentValues();
        try {
            for (Field field : instance.getFields()) {
                if (BaseModel.DEBUG) {
                    Log.v(TAG, "getContentValues: " + field.getName());
                }
                if (isInt(field)) {
                    contentValues.put(field.getName(), field.getInt(instance));
                } else if (isLong(field)) {
                    contentValues.put(field.getName(), field.getLong(instance));
                } else if (isFloat(field)) {
                    contentValues.put(field.getName(), field.getFloat(instance));
                } else if (isBoolean(field)) {
                    contentValues.put(field.getName(), field.getBoolean(instance) ? 1 : 0);
                } else if (isString(field)) {
                    contentValues.put(field.getName(), (String) field.get(instance));
                } else if (isByteArray(field)) {
                    contentValues.put(field.getName(), (byte[]) field.get(instance));
                } else {
                    throw new UnsupportedClassVersionError(field.getType() + " is not supported");
                }
            }
        } catch (IllegalAccessException e) {
            Log.e(TAG, "Access wasn't public. Should never happen, though.", e);
        }

        return contentValues;
    }

    /**
     * Creates a JSONObject from the given model
     */
    static <A extends BaseModel<A>> JSONObject getJSONObject(A instance) {
        JSONObject object = new JSONObject();
        try {
            for (Field field : instance.getFields()) {
                if (isInt(field)) {
                    object.put(field.getName(), field.getInt(instance));
                } else if (isLong(field)) {
                    object.put(field.getName(), field.getLong(instance));
                } else if (isFloat(field)) {
                    object.put(field.getName(), field.getFloat(instance));
                } else if (isBoolean(field)) {
                    object.put(field.getName(), field.getBoolean(instance));
                } else if (isString(field)) {
                    object.put(field.getName(), field.get(instance));
                } else if (isByteArray(field)) {
                    byte[] bytes = (byte[]) field.get(instance);
                    if (bytes != null) {
                        object.put(field.getName(), Base64.encodeToString(bytes, Base64.DEFAULT));
                    }
                } else {
                    throw new UnsupportedClassVersionError(field.getType() + " is not supported");
                }
            }
        } catch (IllegalAccessException e) {
            Log.e(TAG, "Access wasn't public. Should never happen, though.", e);
        } catch (JSONException e) {
            Log.e(TAG, "Trouble parsing the json object", e);
        }
        return object;
    }
}
