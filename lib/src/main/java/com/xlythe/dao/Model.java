package com.xlythe.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Handler;
import android.util.Base64;
import android.util.Log;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * A class that serializes its fields into a database
 *
 * All classes that extend Model must have a constructor that takes a context (and nothing else).
 */
public abstract class Model<T extends Model> implements Serializable {
    protected static final transient String TAG = "Deception";//Model.class.getSimpleName();
    private static transient int DATABASE_VERSION = 1;
    private static transient boolean RETAIN_DATA_ON_UPGRADE = false;

    private static final transient BiMap<Class, Observer> OBSERVERS = HashBiMap.create();

    private final transient Class<T> mClass;
    private final transient Context mContext;
    private final transient Handler mHandler = new Handler();
    private transient ModelDataSource mDataSource;
    private transient Field[] mFields;

    protected static void registerObserver(Class clazz, Observer observer) {
        OBSERVERS.put(clazz, observer);
    }

    protected static void unregisterObserver(Observer observer) {
        OBSERVERS.inverse().remove(observer);
    }

    public static void setDatabaseVersion(int version, boolean retainDataOnUpgrade) {
        DATABASE_VERSION = version;
        RETAIN_DATA_ON_UPGRADE = retainDataOnUpgrade;
    }

    public Model(Class<T> clazz, Context context) {
        mClass = clazz;
        mContext = context;
    }

    public Context getContext() {
        return mContext;
    }

    protected ModelDataSource getDataSource() {
        return mDataSource;
    }

    protected Handler getHandler() {
        return mHandler;
    }

    protected void open() {
        if (mDataSource != null) {
            mDataSource.close();
        }

        mDataSource = new ModelDataSource(getContext());
        try {
            mDataSource.open();
        } catch(SQLException e) {
            e.printStackTrace();
        }
    }

    protected void close() {
        if (mDataSource != null) {
            mDataSource.close();
            mDataSource = null;
        }
    }

    protected void save() {
        open();
        mDataSource.save((T) this);
        if (OBSERVERS.containsKey(mClass)) {
            OBSERVERS.get(mClass).onChange();
        }
        close();
    }

    protected void delete() {
        open();
        mDataSource.delete((T) this);
        if (OBSERVERS.containsKey(mClass)) {
            OBSERVERS.get(mClass).onChange();
        }
        close();
    }

    protected static <B extends Model> B create(Class<B> clazz, Context context) {
        try {
            B instance = clazz.getDeclaredConstructor(Context.class).newInstance(context);
            return instance;
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "Failed to find constructor", e);
        } catch (IllegalAccessException e) {
            Log.e(TAG, "Constructor was not public", e);
        } catch (InvocationTargetException e) {
            Log.e(TAG, "Constructor exploded", e);
        } catch (InstantiationException e) {
            Log.e(TAG, "Constructor exploded", e);
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        if (o.getClass() != getClass()) return false;
        for (Field field : getFields()) {
            if (isUnique(field)) {
                try {
                    Object me = field.get(this);
                    Object them = field.get(o);
                    if (me == them) {
                        continue;
                    } else if (me == null) {
                        return false;
                    }

                    if (me.equals(them)) {
                        continue;
                    } else {
                        return false;
                    }
                } catch (IllegalAccessException e) {
                    Log.e(TAG, "Should not happen", e);
                }
            }
        }
        return true;
    }

    private static <A extends Model> long count(Class<A> clazz, Context context, Param... params) {
        final Model<A> model = create(clazz, context);

        // Open the db
        model.open();

        // Create params
        final long size = model.mDataSource.count(params);

        // Close the db
        model.close();

        return size;
    }

    private static <A extends Model> List<A> query(Class<A> clazz, Context context, Param... params) {
        final Model<A> model = create(clazz, context);

        // Open the db
        model.open();

        // Create params
        final List<A> cache = model.mDataSource.query(params);

        // Close the db
        model.close();

        return cache;
    }

    private Field[] getFields() {
        if (mFields != null) {
            return mFields;
        }
        ArrayList<Field> fields = new ArrayList<>();
        mFields = mClass.getDeclaredFields();
        for (Field field : mFields) {
            field.setAccessible(true);
            if (!Modifier.isTransient(field.getModifiers())) {
                fields.add(field);
            }
        }
        mFields = fields.toArray(new Field[fields.size()]);
        return mFields;
    }

    private Field getField(String name) throws NoSuchFieldException {
        Field field = mClass.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    /**
     * Creates an instance of the model when given a cursor
     * */
    protected T parse(Cursor cursor) {
        T instance = create(mClass, mContext);
        try {
            for (Field field : getFields()) {
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
     * Creates an instance of the model when given a json object
     * */
    protected T parse(JSONObject object) {
        T instance = create(mClass, mContext);
        try {
            for (Field field : getFields()) {
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
     * Creates an instance of the model when given params
     * */
    protected T parse(Param... params) {
        T instance = create(mClass, mContext);
        try {
            for (Param param : params) {
                Field field = getField(param.getKey());
                if (isInt(field)) {
                    field.setInt(instance, Integer.parseInt(param.getValue()));
                } else if (isLong(field)) {
                    field.setLong(instance, Long.parseLong(param.getValue()));
                } else if (isFloat(field)) {
                    field.setFloat(instance, Float.parseFloat(param.getValue()));
                } else if (isBoolean(field)) {
                    field.setBoolean(instance, Boolean.parseBoolean(param.getValue()));
                } else if (isString(field)) {
                    // It's a string! However, strings look like 'this', so chop off the quotes
                    field.set(instance, param.getValue().substring(1, param.getValue().length() - 1));
                } else if (isByteArray(field)) {
                    field.set(instance, param.getValue().getBytes());
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

    private ContentValues getContentValues(T instance) {
        ContentValues contentValues = new ContentValues();
        try {
            for (Field field : getFields()) {
                if (isInt(field)) {
                    contentValues.put(field.getName(), field.getInt(instance));
                } else if (isLong(field)) {
                    contentValues.put(field.getName(), field.getLong(instance));
                } else if (isFloat(field)) {
                    contentValues.put(field.getName(), field.getFloat(instance));
                } else if (isBoolean(field)) {
                    contentValues.put(field.getName(), field.getBoolean(instance));
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
     * Creates an instance of the model when given a json object
     * */
    protected JSONObject getJSONObject(T instance) {
        JSONObject object = new JSONObject();
        try {
            for (Field field : getFields()) {
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

    private String[] getStringArray() {
        Field[] fields = getFields();
        String[] array = new String[fields.length];
        for (int i = 0; i < fields.length; i++) {
            array[i] = getString(fields[i]);
        }
        return array;
    }

    private String getString(Field field) {
        if (isInt(field)) {
            return "integer";
        } else if (isLong(field)) {
            return "integer";
        } else if (isFloat(field)) {
            return "float";
        } else if (isBoolean(field)) {
            return "integer";
        } else if (isString(field)) {
            return "text";
        } else if (isByteArray(field)) {
            return "blob";
        } else {
            throw new UnsupportedClassVersionError(field.getType() + " is not supported");
        }
    }

    private static boolean isInt(Field field) {
        return field.getType() == Integer.TYPE;
    }

    private static boolean isLong(Field field) {
        return field.getType() == Long.TYPE;
    }

    private static boolean isFloat(Field field) {
        return field.getType() == Float.TYPE;
    }

    private static boolean isBoolean(Field field) {
        return field.getType() == Boolean.TYPE;
    }

    private static boolean isString(Field field) {
        return field.getType() == String.class;
    }

    private static boolean isByteArray(Field field) {
        return field.getType() == byte[].class;
    }

    private boolean isUnique(Field field) {
        return field.getAnnotation(Unique.class) != null;
    }

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Unique {}

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Version {
        int value() default 1;
    }

    protected static class Query<Q extends Model> {
        private final Class<Q> mClass;
        private final Context mContext;
        private final ArrayList<Param> mParams = new ArrayList<>();

        public Query(Class<Q> clazz, Context context) {
            mClass = clazz;
            mContext = context;
        }

        public Query where(String key, Object value) {
            mParams.add(new Param(key, value));
            return this;
        }

        public Query where(Param... params) {
            mParams.addAll(Arrays.asList(params));
            return this;
        }

        public long count() {
            return Model.count(mClass, mContext, getParams());
        }

        public List<Q> all() {
            return Model.query(mClass, mContext, getParams());
        }

        public Q first() {
            List<Q> results = Model.query(mClass, mContext, getParams());
            return results.isEmpty() ? null : results.get(0);
        }

        public Q create() {
            Q instance = Model.create(mClass, mContext);
            instance = (Q) instance.parse(getParams());
            instance.save();
            return instance;
        }

        protected Context getContext() {
            return mContext;
        }

        protected Param[] getParams() {
            return mParams.toArray(new Param[mParams.size()]);
        }

        protected Class<Q> getModelClass() {
            return mClass;
        }
    }

    public interface Observer {
        void onChange();
    }

    public interface Callback<T> {
        void onSuccess(T object);
    }

    public static final class Param {
        private final String key;
        private final String value;

        public Param(String key, Object value) {
            this.key = key;

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

        @Override
        public String toString() {
            return key + ":" + value;
        }
    }

    protected class ModelDataSource {
        // Database fields
        private SQLiteDatabase database;
        private ModelHelper dbHelper;
        private String[] columns;

        public ModelDataSource(Context context) {
            dbHelper = new ModelHelper(context, getDbName() + ".db");
        }

        public void open() throws SQLException {
            database = dbHelper.getWritableDatabase();
            dbHelper.onCreate(database);
        }

        public void close() {
            dbHelper.close();
        }

        private Param[] getUniqueParams(T instance) {
            ArrayList<Param> params = new ArrayList<>();
            try {
                for (Field field : getFields()) {
                    if (isUnique(field) && field.get(instance) != null) {
                        params.add(new Param(field.getName(), field.get(instance)));
                    }
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
            return params.toArray(new Param[params.size()]);
        }

        private String createQuery(Param... params) {
            StringBuilder query = new StringBuilder();
            for (Param param : params) {
                if (query.length() > 0) {
                    query.append(" AND ");
                }
                query.append(param.getKey() + " = " + param.getValue());
            }
            return query.toString();
        }

        public String getDbName() {
            return mClass.getPackage().getName();
        }

        public String getTableName() {
            return mClass.getSimpleName();
        }

        public String[] getColumns() {
            if (columns != null) {
                return columns;
            }
            Field[] fields = getFields();
            columns = new String[fields.length];
            for (int i=0; i<fields.length; i++) {
                Field field = fields[i];
                columns[i] = field.getName();
            }
            return columns;
        }

        public Cursor getRows() {
            return database.query(getTableName(), getColumns(), null, null, null, null, null);
        }

        public void save(T instance) {
            Log.d(TAG, "Saving");
            ContentValues values = getContentValues(instance);
            Param[] params = getUniqueParams(instance);
            List<T> currentItems = query(params);
            if (currentItems.isEmpty()) {
                Log.d(TAG, "Creating new entry");
                database.insert(getTableName(), null, values);
            } else {
                Log.d(TAG, "Updating existing entry");
                database.update(getTableName(), values, createQuery(params), null);
            }
        }

        public void delete(T instance) {
            Log.d(TAG, "Deleting");
            int rowsDeleted = database.delete(getTableName(), createQuery(getUniqueParams(instance)), null);
            Log.d(TAG, "Removed " + rowsDeleted + " rows");
        }

        public long count(Param... params) {
            return DatabaseUtils.queryNumEntries(database, getTableName(), createQuery(params));
        }

        public List<T> query(Param... params) {
            List<T> list = new ArrayList<T>();
            Cursor cursor = database.query(getTableName(), getColumns(), createQuery(params), null, null, null, null);
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                list.add(parse(cursor));
                cursor.moveToNext();
            }
            cursor.close();
            return list;
        }

        public List<T> getAll() {
            List<T> list = new ArrayList<T>();
            Cursor cursor = database.query(getTableName(), getColumns(), null, null, null, null, null);
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                list.add(parse(cursor));
                cursor.moveToNext();
            }
            cursor.close();
            return list;
        }

        private class ModelHelper extends SQLiteOpenHelper {
            // Database creation sql statement
            protected final String DATABASE_CREATE;

            public ModelHelper(Context context, String databaseName) {
                super(context, databaseName, null, DATABASE_VERSION);

                StringBuilder builder = new StringBuilder("create table if not exists " + getTableName() +
                        "(_id integer primary key autoincrement");

                Field[] fields = getFields();
                String[] types = getStringArray();
                for (int i=0; i<fields.length; i++) {
                    builder.append(", " + fields[i].getName() + " " + types[i]);
                }

                builder.append(");");
                DATABASE_CREATE = builder.toString();
            }

            @Override
            public void onCreate(SQLiteDatabase database) {
                database.execSQL(DATABASE_CREATE);
            }

            @Override
            public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
                if (RETAIN_DATA_ON_UPGRADE) {
                    Field[] fields = getFields();
                    for (int i = oldVersion + 1; i <= newVersion; i++) {
                        for (Field field : fields) {
                            if (field.isAnnotationPresent(Version.class)) {
                                int value = field.getAnnotation(Version.class).value();
                                if (value == i) {
                                    String type = getString(field);
                                    database.execSQL("ALTER TABLE " + getTableName() + " ADD COLUMN " + field.getName() + " " + type);
                                }
                            }
                        }
                    }
                } else {
                    // Drop all existing tables
                    List<String> tablesToDrop = new LinkedList<>();
                    Cursor cursor = database.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null);
                    cursor.moveToFirst();
                    while (!cursor.isAfterLast()) {
                        String table = cursor.getString(0);
                        cursor.moveToNext();
                        if ("android_metadata".equals(table)) {
                            continue;
                        }
                        if ("sqlite_sequence".equals(table)) {
                            continue;
                        }
                        tablesToDrop.add(table);
                    }
                    for (String table : tablesToDrop) {
                        String sql = "drop table if exists " + table;
                        database.execSQL(sql);
                    }

                    // Recreate this table (other tables will be created as needed)
                    onCreate(database);
                }
            }
        }
    }
}