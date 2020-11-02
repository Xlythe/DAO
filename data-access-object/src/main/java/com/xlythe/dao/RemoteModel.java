package com.xlythe.dao;

import android.content.Context;
import android.os.Handler;
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
public abstract class RemoteModel<T extends RemoteModel> extends Model<T> {
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

        final Handler handler = new Handler();
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

        final Handler handler = new Handler();
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

    protected static class Query<Q extends RemoteModel<Q>> extends Model.Query<Q> {
        private String mUrl;
        private Handler mHandler = new Handler();

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

        public List<Q> all(final Callback<List<Q>> callback) {
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
                        final JSONArray array = response.asJSONArray();
                        final Q model = newInstance(getModelClass(), getContext());
                        try {
                            model.open();
                            // Add all the items from the server to the local cache db
                            List<Q> list = new ArrayList<Q>(array.length());
                            for (int i = 0; i < array.length(); i++) {
                                Q instance = (Q) Transcriber.inflate(newInstance(getModelClass(), getContext()), array.getJSONObject(i));
                                list.add(instance);
                                model.getDataSource().save(instance);
                            }

                            // Clean up any items in the cache that didn't exist on the server
                            Log.d(TAG, "Checking cache for any old variables");
                            for (Q instance : cache) {
                                if (!list.contains(instance)) {
                                    Log.d(TAG, "Deleting instance");
                                    instance.delete();
                                }
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

        public Q first(final Callback<Q> callback) {
            Q cache = super.first();

            if (callback == null) {
                Log.w(TAG, "No callback set, returning cached data");
                return cache;
            }

            // Query the server for new results (this will update the database)
            all(new Callback<List<Q>>() {
                @Override
                public void onSuccess(List<Q> results) {
                    callback.onSuccess(results.isEmpty() ? null : results.get(0));
                }

                @Override
                public void onFailure(Throwable throwable) {
                    callback.onFailure(throwable);
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
