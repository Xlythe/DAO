package com.xlythe.dao;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.xlythe.dao.remote.DefaultServer;
import com.xlythe.dao.remote.JSONResult;
import com.xlythe.dao.remote.Server;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import static com.xlythe.dao.Util.newInstance;

/**
 * A class that serializes its fields into a database
 *
 * All classes that extend Model must have a constructor that takes a context (and nothing else).
 */
public abstract class RemoteModel<T extends RemoteModel<T>> extends Model<T> {
    private static transient Server sServer;

    private static Server getServer(Context context) {
        if (sServer == null) {
            sServer = new DefaultServer(context);
        }
        return sServer;
    }

    public static void setServer(Server server) {
        sServer = server;
    }

    private String mUrl;

    public RemoteModel(Context context) {
        super(context);
    }

    protected void setUrl(String url) {
        mUrl = url;
    }

    @SuppressWarnings("unchecked")
    private T getModel() {
        return (T) this;
    }

    protected void save(final Callback<T> callback) {
        if (mUrl == null) {
            throw new IllegalStateException("No url set");
        }
        if (callback == null) {
            Log.w(TAG, "No callback set, ignoring");
            return;
        }

        Handler handler = new Handler(Looper.getMainLooper());
        getServer(getContext()).post(mUrl, Transcriber.getJSONObject(getModel()), new Callback<JSONResult>() {
            @Override
            public void onSuccess(JSONResult response) {
                handler.post(() -> {
                    final T model = getModel();
                    // Add all the items from the server to the local cache db
                    Transcriber.inflate(model, response.asJSONObject());
                    model.save();
                    callback.onSuccess(model);
                });
            }

            @Override
            public void onFailure(Throwable throwable) {
                handler.post(() -> {
                    Log.e(TAG, "Failed: ", throwable);
                    callback.onFailure(throwable);
                });
            }
        });
    }

    protected void delete(final Callback<Void> callback) {
        if (mUrl == null) {
            throw new IllegalStateException("No url set");
        }
        if (callback == null) {
            Log.w(TAG, "No callback set, ignoring");
            return;
        }

        Handler handler = new Handler(Looper.getMainLooper());
        getServer(getContext()).delete(mUrl + "/" + getUniqueKey(), new Callback<JSONResult>() {
            @Override
            public void onSuccess(JSONResult response) {
                handler.post(() -> {
                    final T model = getModel();
                    try {
                        model.delete();
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to delete cache", e);
                    }
                    callback.onSuccess(null);
                });
            }

            @Override
            public void onFailure(Throwable throwable) {
                handler.post(() -> {
                    Log.e(TAG, "Failed: ", throwable);
                    callback.onFailure(throwable);
                });
            }
        });
    }

    public static class Query<Q extends RemoteModel<Q>> extends Model.Query<Q> {
        private String mUrl;
        private final Handler mHandler = new Handler(Looper.getMainLooper());

        public Query(Class<Q> clazz, Context context) {
            super(clazz, context);
        }

        public Query<Q> url(String url) {
            mUrl = url;
            return this;
        }

        @Override
        public List<Q> all() {
            return all(null);
        }

        public List<Q> all(Callback<List<Q>> callback) {
            final List<Q> cache = super.all();

            // Don't ping the server if a callback wasn't sent
            if (callback == null) {
                Log.w(TAG, "No callback set, returning cached data");
                return cache;
            }

            JSONObject params = new JSONObject();
            for(Param param : getParams()) {
                try {
                    params.put(param.getKey(), param.getUnformattedValue().toString());
                } catch (JSONException e) {
                    throw new IllegalArgumentException("Invalid value for key " + param.getKey(), e);
                }
            }

            getServer(getContext()).get(mUrl, params, new Callback<JSONResult>() {
                @Override
                public void onSuccess(JSONResult response) {
                    mHandler.post(() -> {
                        final JSONArray array;
                        try {
                            array = response.asJSONArray();
                        } catch (RuntimeException e) {
                            onFailure(e);
                            return;
                        }

                        final Q model = newInstance(getModelClass(), getContext());
                        try {
                            model.open();

                            // Clean up the old cache
                            model.getDataSource().delete(getParams());

                            // Add all the items from the server to the local cache db
                            List<Q> list = new ArrayList<>(array.length());
                            for (int i = 0; i < array.length(); i++) {
                                Q instance = Transcriber.inflate(newInstance(getModelClass(), getContext()), array.getJSONObject(i));
                                list.add(instance);
                                model.getDataSource().save(instance);
                            }

                            // Give the callback the new data
                            callback.onSuccess(list);
                        } catch (JSONException e) {
                            Log.e(TAG, "Exception parsing fields from JSON Object", e);
                            callback.onFailure(e);
                        } finally {
                            model.close();
                        }
                    });
                }

                @Override
                public void onFailure(Throwable throwable) {
                    mHandler.post(() -> {
                        Log.e(TAG, "Failed: ", throwable);
                        callback.onFailure(throwable);
                    });
                }
            });

            return cache;
        }

        @Override
        public Q first() {
            return first(null);
        }

        public Q first(Callback<Q> callback) {
            Q cache = super.first();

            if (callback == null) {
                Log.w(TAG, "No callback set, returning cached data");
                return cache;
            }

            String primaryKey = null;
            JSONObject params = new JSONObject();
            for(Param param : getParams()) {
                if (param.isPrimaryKey()) {
                    primaryKey = param.getValue();
                    continue;
                }

                try {
                    params.put(param.getKey(), param.getUnformattedValue().toString());
                } catch (JSONException e) {
                    throw new IllegalArgumentException("Invalid value for key " + param.getKey(), e);
                }
            }

            // If we can look up the value directly, rather than as a list, we will prefer to do so.
            String url = mUrl;
            if (primaryKey != null) {
                if (!url.endsWith("/")) {
                    url += "/";
                }
                url = url + primaryKey;
            }

            getServer(getContext()).get(url, params, new Callback<JSONResult>() {
                @Override
                public void onSuccess(JSONResult response) {
                    mHandler.post(() -> {
                        final JSONObject object;
                        try {
                            object = getFirst(response);
                        } catch (RuntimeException e) {
                            onFailure(e);
                            return;
                        }

                        final Q model = newInstance(getModelClass(), getContext());
                        try {
                            model.open();

                            // Clean up the old cache
                            model.getDataSource().delete(getParams());

                            // Add all the items from the server to the local cache db
                            Q instance = Transcriber.inflate(newInstance(getModelClass(), getContext()), object);
                            model.getDataSource().save(instance);

                            // Give the callback the new data
                            callback.onSuccess(instance);
                        } finally {
                            model.close();
                        }
                    });
                }

                @Override
                public void onFailure(Throwable throwable) {
                    mHandler.post(() -> {
                        Log.e(TAG, "Failed: ", throwable);
                        callback.onFailure(throwable);
                    });
                }

                private JSONObject getFirst(JSONResult response) {
                    if (response.isJSONObject()) {
                        return response.asJSONObject();
                    }

                    try {
                        return response.asJSONArray().getJSONObject(0);
                    } catch(JSONException | IndexOutOfBoundsException e) {
                        throw new RuntimeException(e);
                    }
                }
            });

            // Return the cache
            return cache;
        }

        @Override
        public Q insert() {
            return insert(null);
        }

        public Q insert(final Callback<Q> callback) {
            final Q cache = super.insert();

            // Don't ping the server if a callback wasn't sent
            if (callback == null) {
                Log.w(TAG, "No callback set, returning cached data");
                return cache;
            }

            getServer(getContext()).post(mUrl, Transcriber.getJSONObject(cache), new Callback<JSONResult>() {
                @Override
                public void onSuccess(JSONResult response) {
                    mHandler.post(() -> {
                        final Q model = newInstance(getModelClass(), getContext());
                        try {
                            model.open();

                            // Add all the items from the server to the local cache db
                            Q instance = Transcriber.inflate(newInstance(getModelClass(), getContext()), response.asJSONObject());
                            model.getDataSource().save(instance);

                            // Give the callback the new data
                            callback.onSuccess(instance);
                        } finally {
                            model.close();
                        }
                    });
                }

                @Override
                public void onFailure(Throwable throwable) {
                    mHandler.post(() -> {
                        Log.e(TAG, "Failed: ", throwable);
                        callback.onFailure(throwable);
                    });
                }
            });

            return null;
        }
    }
}
