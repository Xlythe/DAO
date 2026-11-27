package com.xlythe.dao;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
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
import java.util.Collections;
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
                    try {
                        Transcriber.inflate(model, response.asJSONObject());
                        model.save();
                    } catch (JSONException | RuntimeException e) {
                        callback.onFailure(e);
                        return;
                    }
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
        getServer(getContext()).delete(Uri.parse(mUrl).buildUpon().appendPath(String.valueOf(getUniqueKey())).build().toString(), new Callback<JSONResult>() {
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
            return refresh(super.all(), asJsonObject(), true, callback);
        }

        @Override
        public List<Q> limit(int limit) {
            return limit(limit, null);
        }

        public List<Q> limit(int limit, Callback<List<Q>> callback) {
            return refresh(super.limit(limit), pageParams(limit, null), false, callback);
        }

        @Override
        public List<Q> limit(int limit, int offset) {
            return limit(limit, offset, null);
        }

        public List<Q> limit(int limit, int offset, Callback<List<Q>> callback) {
            return refresh(super.limit(limit, offset), pageParams(limit, offset), false, callback);
        }

        private JSONObject pageParams(int limit, Integer offset) {
            JSONObject params = asJsonObject();
            try {
                params.put("limit", limit);
                if (offset != null) params.put("offset", offset);
            } catch (JSONException e) {
                throw new IllegalArgumentException(e);
            }
            return params;
        }

        private List<Q> refresh(List<Q> cache, JSONObject params, boolean replace, Callback<List<Q>> callback) {
            if (callback == null) return cache;
            Param[] selection = getParams();
            getServer(getContext()).get(mUrl, params, new Callback<JSONResult>() {
                @Override public void onSuccess(JSONResult response) {
                    mHandler.post(() -> {
                        List<Q> list = new ArrayList<>();
                        Q model = newInstance(getModelClass(), getContext());
                        try {
                            JSONArray array = response.asJSONArray();
                            for (int i = 0; i < array.length(); i++) {
                                list.add(Transcriber.inflate(newInstance(getModelClass(), getContext()), array.getJSONObject(i)));
                            }
                            model.open();
                            model.getDataSource().cacheResponse(list, selection, replace);
                        } catch (JSONException | RuntimeException e) {
                            callback.onFailure(e);
                            return;
                        } finally {
                            model.close();
                        }
                        callback.onSuccess(list);
                    });
                }

                @Override public void onFailure(Throwable throwable) {
                    mHandler.post(() -> callback.onFailure(throwable));
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
            for(Param param : getParams()) {
                if (param.isPrimaryKey()) {
                    primaryKey = param.getParameterizedValue();
                    break;
                }
            }

            // If we can look up the value directly, rather than as a list, we will prefer to do so.
            String url = mUrl;
            if (primaryKey != null) {
                url = Uri.parse(url).buildUpon().appendPath(primaryKey).build().toString();
            }

            JSONObject params = asJsonObject(true);
            if (primaryKey == null) {
                try {
                    params.put("limit", 1);
                } catch (JSONException e) {
                    throw new IllegalArgumentException("Invalid value for key limit", e);
                }
            }

            getServer(getContext()).get(url, params, new Callback<JSONResult>() {
                @Override
                public void onSuccess(JSONResult response) {
                    mHandler.post(() -> {
                        Q result = null;
                        try {
                            JSONObject object = getFirst(response);
                            if (object != null) {
                                result = Transcriber.inflate(newInstance(getModelClass(), getContext()), object);
                                result.open();
                                try {
                                    result.getDataSource().save(result);
                                } finally {
                                    result.close();
                                }
                            }
                        } catch (JSONException | RuntimeException e) {
                            callback.onFailure(e);
                            return;
                        }
                        callback.onSuccess(result);
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
                        JSONArray rows = response.asJSONArray();
                        return rows.length() == 0 ? null : rows.getJSONObject(0);
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
                        Q result = newInstance(getModelClass(), getContext());
                        try {
                            JSONObject fields = Transcriber.getJSONObject(cache);
                            Transcriber.inflate(result, fields);
                            Transcriber.inflate(result, response.asJSONObject());
                            result.open();
                            result.getDataSource().cacheResponse(Collections.singletonList(result),
                                    new Param[]{new Param(_ID, cache.get_Id())}, true);
                        } catch (JSONException | RuntimeException e) {
                            callback.onFailure(e);
                            return;
                        } finally {
                            result.close();
                        }
                        callback.onSuccess(result);
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

        private JSONObject asJsonObject() {
            return asJsonObject(false);
        }

        private JSONObject asJsonObject(boolean skipPrimaryKey) {
            JSONObject params = new JSONObject();
            for(Param param : getParams()) {
                if (skipPrimaryKey && param.isPrimaryKey()) {
                    continue;
                }

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        params.put(param.getKey(), JSONObject.wrap(param.getUnformattedValue()));
                    } else {
                        params.put(param.getKey(), param.getUnformattedValue() == null ? JSONObject.NULL : param.getUnformattedValue().toString());
                    }
                } catch (JSONException e) {
                    throw new IllegalArgumentException("Invalid value for key " + param.getKey(), e);
                }
            }
            return params;
        }
    }
}
