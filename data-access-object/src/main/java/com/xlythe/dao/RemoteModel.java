package com.xlythe.dao;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.PersistentCookieStore;
import com.loopj.android.http.RequestParams;

import org.apache.http.Header;
import org.apache.http.entity.ByteArrayEntity;
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
    private static transient Server SERVER;

    private static Server getServer(Context context) {
        if (SERVER == null) {
            SERVER = new DefaultServer(context);
        }
        return SERVER;
    }

    public static void setServer(Server server) {
        SERVER = server;
    }

    private String mUrl;

    public RemoteModel(Context context) {
        super(context);
    }

    protected void setUrl(String url) {
        mUrl = url;
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
        getServer(getContext()).post(mUrl, Transcriber.getJSONObject((T) this).toString(), new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, String responseString) {
                try {
                    onSuccess(statusCode, headers, new JSONObject(responseString));
                } catch (JSONException | NullPointerException e) {
                    Log.e(TAG, "Failed to parse " + responseString, e);
                    onFailure(statusCode, headers, responseString, e);
                }
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, final JSONObject response) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        final T model = (T) RemoteModel.this;
                        // Add all the items from the server to the local cache db
                        Transcriber.inflate(model, response);
                        model.save();
                        callback.onSuccess(model);
                    }
                });
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString, final Throwable throwable) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Log.e(TAG, "Failed: ", throwable);
                        callback.onFailure(throwable);
                    }
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
        getServer(getContext()).delete(mUrl + "/" + getUniqueKey(), new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, String responseString) {
                onSuccess(statusCode, headers, (JSONObject) null);
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, final JSONObject response) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        final T model = (T) RemoteModel.this;
                        try {
                            model.delete();
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to delete cache", e);
                        }
                        callback.onSuccess(null);
                    }
                });
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString, final Throwable throwable) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Log.e(TAG, "Failed: ", throwable);
                        callback.onFailure(throwable);
                    }
                });
            }
        });
    }

    protected static class Query<Q extends RemoteModel> extends Model.Query<Q> {
        private String mUrl;
        private Handler mHandler = new Handler();

        public Query(Class<Q> clazz, Context context) {
            super(clazz, context);
        }

        public Query url(String url) {
            mUrl = url;
            return this;
        }

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

            final RequestParams requestParams = new RequestParams();
            for(Param param : getParams()) {
                requestParams.add(param.getKey(), param.getUnformattedValue().toString());
            }

            getServer(getContext()).get(mUrl, requestParams, new JsonHttpResponseHandler() {
                @Override
                public void onSuccess(int statusCode, Header[] headers, String responseString) {
                    try {
                        onSuccess(statusCode, headers, new JSONArray(responseString));
                    } catch (JSONException | NullPointerException e) {
                        Log.e(TAG, "Failed to parse " + responseString, e);
                        onFailure(statusCode, headers, responseString, e);
                    }
                }

                @Override
                public void onSuccess(int statusCode, Header[] headers, final JSONArray response) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            final Q model = newInstance(getModelClass(), getContext());
                            try {
                                model.open();
                                // Add all the items from the server to the local cache db
                                List<Q> list = new ArrayList<Q>(response.length());
                                for (int i = 0; i < response.length(); i++) {
                                    Q instance = (Q) Transcriber.inflate(newInstance(getModelClass(), getContext()), response.getJSONObject(i));
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
                        }
                    });
                }

                @Override
                public void onFailure(int statusCode, Header[] headers, String responseString, final Throwable throwable) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Log.e(TAG, "Failed: ", throwable);
                            callback.onFailure(throwable);
                        }
                    });
                }
            });

            return cache;
        }

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

            getServer(getContext()).post(mUrl, Transcriber.getJSONObject(cache).toString(), new JsonHttpResponseHandler() {
                @Override
                public void onSuccess(int statusCode, Header[] headers, String responseString) {
                    try {
                        onSuccess(statusCode, headers, new JSONObject(responseString));
                    } catch (JSONException | NullPointerException e) {
                        Log.e(TAG, "Failed to parse " + responseString, e);
                        onFailure(statusCode, headers, responseString, e);
                    }
                }

                @Override
                public void onSuccess(int statusCode, Header[] headers, final JSONObject response) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            final Q model = newInstance(getModelClass(), getContext());
                            try {
                                model.open();

                                // Add all the items from the server to the local cache db
                                Q instance = (Q) Transcriber.inflate(newInstance(getModelClass(), getContext()), response);
                                model.getDataSource().save(instance);

                                // Give the callback the new data
                                callback.onSuccess(instance);
                            } finally {
                                model.close();
                            }
                        }
                    });
                }

                @Override
                public void onFailure(int statusCode, Header[] headers, String responseString, final Throwable throwable) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Log.e(TAG, "Failed: ", throwable);
                            callback.onFailure(throwable);
                        }
                    });
                }
            });

            return null;
        }
    }

    public interface Callback<T> {
        void onSuccess(T object);
        void onFailure(Throwable throwable);
    }

    public interface Server {
        void get(String url, RequestParams params, JsonHttpResponseHandler responseHandler);
        void post(String url, String json, JsonHttpResponseHandler responseHandler);
        void put(String url, RequestParams params, JsonHttpResponseHandler responseHandler);
        void delete(String url, JsonHttpResponseHandler responseHandler);
    }

    private static class DefaultServer implements Server {
        private final AsyncHttpClient mClient = new AsyncHttpClient();
        private final Context mContext;

        DefaultServer(Context context) {
            mContext = context.getApplicationContext();
            mClient.setCookieStore(new PersistentCookieStore(mContext));
        }

        @Override
        public void get(String url, RequestParams params, JsonHttpResponseHandler responseHandler) {
            if (DEBUG) {
                Log.d(TAG, "get="+url+", params="+params);
            }
            mClient.get(url, params, responseHandler);
        }

        @Override
        public void post(String url, String json, JsonHttpResponseHandler responseHandler) {
            if (DEBUG) {
                Log.d(TAG, "post=" + url + ", json=" + json);
            }
            ByteArrayEntity entity = new ByteArrayEntity(json.getBytes());
            mClient.post(mContext, url, entity, "application/json", responseHandler);
        }

        @Override
        public void put(String url, RequestParams params, JsonHttpResponseHandler responseHandler) {
            if (DEBUG) {
                Log.d(TAG, "put="+url+", params="+params);
            }
            mClient.put(url, params, responseHandler);
        }

        @Override
        public void delete(String url, JsonHttpResponseHandler responseHandler) {
            if (DEBUG) {
                Log.d(TAG, "delete="+url);
            }
            mClient.delete(url, responseHandler);
        }
    };
}
