package com.xlythe.dao;

import android.content.Context;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

import static com.xlythe.dao.Model.TAG;

public class Util {
    static boolean isInt(Field field) {
        return field.getType() == Integer.TYPE;
    }

    static boolean isLong(Field field) {
        return field.getType() == Long.TYPE;
    }

    static boolean isFloat(Field field) {
        return field.getType() == Float.TYPE;
    }

    static boolean isBoolean(Field field) {
        return field.getType() == Boolean.TYPE;
    }

    static boolean isString(Field field) {
        return field.getType() == String.class;
    }

    static boolean isByteArray(Field field) {
        return field.getType() == byte[].class;
    }

    static boolean isUnique(Field field) {
        return field.getAnnotation(Unique.class) != null;
    }

    static int getDatabaseVersion(Class<?> clazz) {
        if (clazz.isAnnotationPresent(Database.class)) {
            Database annotation = clazz.getAnnotation(Database.class);
            return annotation.version();
        }
        return 1;
    }

    static boolean retainDataOnUpgrade(Class<?> clazz) {
        if (clazz.isAnnotationPresent(Database.class)) {
            Database annotation = clazz.getAnnotation(Database.class);
            return annotation.retainDataOnUpgrade();
        }
        return false;
    }

    static String getTableName(Class<?> clazz) {
        if (clazz.isAnnotationPresent(Database.class)) {
            Database annotation = clazz.getAnnotation(Database.class);
            String tableName = annotation.tableName();
            if (!tableName.isEmpty()) {
                return tableName;
            }
        }
        return clazz.getSimpleName();
    }

    static String getDatabaseName(Class<?> clazz) {
        if (clazz.isAnnotationPresent(Database.class)) {
            Database annotation = clazz.getAnnotation(Database.class);
            String databaseName = annotation.name();
            if (!databaseName.isEmpty()) {
                return databaseName + ".db";
            }
        }
        return clazz.getName() + ".db";
    }

    static <B extends BaseModel<B>> B newInstance(Class<B> clazz, Context context) {
        try {
            return clazz.getDeclaredConstructor(Context.class).newInstance(context);
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "Failed to find constructor", e);
        } catch (IllegalAccessException e) {
            Log.e(TAG, "Constructor was not public", e);
        } catch (InvocationTargetException e) {
            Log.e(TAG, "Constructor exploded", e);
        } catch (InstantiationException e) {
            Log.e(TAG, "Unable to instantiate class", e);
        }
        throw new RuntimeException("Your model (" + clazz.getSimpleName() + ") must have a constructor that takes a Context");
    }

    public static <T extends Model<T>> void dropTable(Context context, Class<T> clazz) {
        newInstance(clazz, context).dropTable();
    }
}
