package com.xlythe.dao;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.xlythe.dao.Util.newInstance;

/**
 * A class that serializes its fields into a database
 *
 * All classes that extend Model must have a constructor that takes a context (and nothing else).
 */
public abstract class Model<T extends Model<T>> extends BaseModel<T> {
    static final String TAG = Model.class.getSimpleName();

    private static final Map<Class<? extends Model<?>>, Set<Observer>> OBSERVERS = new HashMap<>();

    protected static void registerObserver(Class<? extends Model<?>> clazz, Observer observer) {
        if (DEBUG) Log.d(TAG, "Registering observer for " + clazz);
        Set<Observer> set = OBSERVERS.get(clazz);
        if (set == null) {
            set = new HashSet<>();
            OBSERVERS.put(clazz, set);
        }
        set.add(observer);
    }

    protected static void unregisterObserver(Class<? extends Model<?>> clazz, Observer observer) {
        if (DEBUG) Log.d(TAG, "Unregistering observer for " + clazz);
        Set<Observer> set = OBSERVERS.get(clazz);
        if (set != null) {
            set.remove(observer);
        }
    }

    public Model(Context context) {
        super(context);
    }

    public Model(Context context, Cursor cursor) {
        super(context);
        Transcriber.inflate(getModel(), cursor);
    }

    @SuppressWarnings("unchecked")
    private T getModel() {
        return (T) this;
    }

    void create() {
        open();
        getDataSource().create(getModel());
        notifyDataSetChanged();
        close();
    }

    protected void save() {
        open();
        getDataSource().save(getModel());
        notifyDataSetChanged();
        close();
    }

    protected void delete() {
        open();
        getDataSource().delete(getModel());
        notifyDataSetChanged();
        close();
    }

    private void notifyDataSetChanged() {
        if (DEBUG) Log.d(TAG, "Notifying observers for " + getModelClass());
        if (OBSERVERS.containsKey(getModelClass())) {
            for (Observer observer : OBSERVERS.get(getModelClass())) {
                observer.onChange();
            }
        } else {
            if (DEBUG) Log.d(TAG, "No observers found");
        }
    }

    protected static class Query<Q extends Model<Q>> {
        private final Class<Q> mClass;
        private final Context mContext;
        private final ArrayList<Param> mParams = new ArrayList<>();
        private String mOrderBy = null;

        public Query(Class<Q> clazz, Context context) {
            mClass = clazz;
            mContext = context;
        }

        public Query<Q> where(String key, Object value) {
            mParams.add(new Param(key, value));
            return this;
        }

        public Query<Q> where(Param... params) {
            mParams.addAll(Arrays.asList(params));
            return this;
        }

        public Query<Q> orderBy(String order) {
            mOrderBy = order;
            return this;
        }

        public long count() {
            Q model = newInstance(getModelClass(), getContext());
            try {
                model.open();
                return model.getDataSource().count(getParams());
            } finally {
                model.close();
            }
        }

        public List<Q> all() {
            Q model = newInstance(getModelClass(), getContext());
            try {
                model.open();
                return model.getDataSource().query(mOrderBy, getParams());
            } finally {
                model.close();
            }
        }

        public Cursor cursor() {
            Q model = newInstance(getModelClass(), getContext());
            model.open();
            return model.getDataSource().cursor(mOrderBy, getParams());
        }

        public Q first() {
            Q model = newInstance(getModelClass(), getContext());
            try {
                model.open();
                return (Q) model.getDataSource().first(mOrderBy, getParams());
            } finally {
                model.close();
            }
        }

        public Q insert() {
            Q instance = newInstance(getModelClass(), getContext());
            instance = (Q) Transcriber.inflate(instance, getParams());
            instance.create();
            return instance;
        }

        protected final Context getContext() {
            return mContext;
        }

        protected Param[] getParams() {
            return mParams.toArray(new Param[mParams.size()]);
        }

        public String getOrderBy() {
            return mOrderBy;
        }

        protected final Class<Q> getModelClass() {
            return mClass;
        }
    }

    public interface Observer {
        void onChange();
    }
}
