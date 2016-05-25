package com.xlythe.dao;

import android.content.ContentValues;
import android.content.Context;
import android.util.Log;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import org.apache.http.Header;
import org.apache.http.entity.ByteArrayEntity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

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

    public RemoteModel(Class<T> clazz, Context context) {
        super(clazz, context);
    }

    // TODO save changes to remote server
    protected void save() {
        super.save();
    }

    // TODO delete on remote server
    protected void delete() {
        super.delete();
    }

    private static <A extends RemoteModel> void query(Class<A> clazz, Context context, String url, final Callback<List<A>> callback, Param... params) {
        Log.d(TAG, "query: " + url);

        final RemoteModel<A> model = Model.create(clazz, context);

        // Open the db
        model.open();

        // Create params
        final RequestParams requestParams = new RequestParams();
        for(Param param : params) {
            requestParams.add(param.getKey(), param.getValue());
        }

        final List<A> cache = model.getDataSource().query(params);

        // Send call to server
        if (callback != null) {
            getServer(context).get(url, requestParams, new JsonHttpResponseHandler() {
                @Override
                public void onSuccess(int statusCode, Header[] headers, final JSONArray response) {
                    model.getHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                // Add all the items from the server to the local cache db
                                List<A> list = new ArrayList<A>(response.length());
                                for (int i = 0; i < response.length(); i++) {
                                    A instance = model.parse(response.getJSONObject(i));
                                    list.add(instance);
                                    model.getDataSource().save(instance);
                                }

                                // Clean up any items in the cache that didn't exist on the server
                                Log.d(TAG, "Checking cache for any old variables");
                                for (A instance : cache) {
                                    if (!list.contains(instance)) {
                                        Log.d(TAG, "Deleting instance");
                                        instance.delete();
                                    }
                                }

                                // Give the callback the new data
                                callback.onSuccess(list);
                            } catch (JSONException e) {
                                Log.e(TAG, "Exception parsing fields from JSON Object", e);
                            }
                            model.close();
                        }
                    });
                }

                @Override
                public void onFailure(int statusCode, Header[] headers, String responseString, final Throwable throwable) {
                    model.getHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            Log.e(TAG, "Failed: ", throwable);
                            model.close();
                        }
                    });
                }
            });
        }
    }

    private static <A extends RemoteModel> void create(Class<A> clazz, Context context, String url, final Callback<A> callback, Param... params) {
        Log.d(TAG, "create: " + url);

        final RemoteModel<A> model = (RemoteModel<A>) create(clazz, context).parse(params);

        // Open the db
        model.open();

        // Create params
        final RequestParams requestParams = new RequestParams();
        for(Param param : params) {
            requestParams.add(param.getKey(), param.getValue());
        }

        // Send call to server
        if (callback != null) {
            getServer(context).post(url, model.getJSONObject((A) model).toString(), new JsonHttpResponseHandler() {
                @Override
                public void onSuccess(int statusCode, Header[] headers, final JSONObject response) {
                    Log.d(TAG, "Creation response: " + response);
                    model.getHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            // Add all the items from the server to the local cache db
                            A instance = model.parse(response);
                            model.getDataSource().save(instance);

                            // Give the callback the new data
                            callback.onSuccess(instance);
                            model.close();
                        }
                    });
                }

                @Override
                public void onFailure(int statusCode, Header[] headers, String responseString, final Throwable throwable) {
                    Log.e(TAG, "Failed: ", throwable);
                    model.getHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onFailure(throwable);
                            model.close();
                        }
                    });
                }
            });
        }
    }

    protected static class Query<Q extends RemoteModel> extends Model.Query<Q> {
        private String mUrl;

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

        public List<Q> all(Callback<List<Q>> callback) {
            // Query the server for new results (this will update the database)
            RemoteModel.query(getModelClass(), getContext(), mUrl, callback, getParams());

            // Return the cache
            return super.all();
        }

        public Q first() {
            return first(null);
        }

        public Q first(final Callback<Q> callback) {
            // Query the server for new results (this will update the database)
            RemoteModel.query(getModelClass(), getContext(), mUrl, new Callback<List<Q>>() {
                @Override
                public void onSuccess(List<Q> results) {
                    if (callback != null) {
                        callback.onSuccess(results.isEmpty() ? null : results.get(0));
                    }
                }

                @Override
                public void onFailure(Throwable throwable) {
                    if (callback != null) {
                        callback.onFailure(throwable);
                    }
                }
            }, getParams());

            // Return the cache
            return super.first();
        }

        public Q create() {
            return create(null);
        }

        public Q create(final Callback<Q> callback) {
            // Tell the server to create the object
            RemoteModel.create(getModelClass(), getContext(), mUrl, callback, getParams());

            // Don't call super. We want the server to create the object, and we'll clone it.
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
        }

        @Override
        public void get(String url, RequestParams params, JsonHttpResponseHandler responseHandler) {
            Log.d(TAG, "get="+url+", params="+params);
            mClient.get(url, params, responseHandler);
        }

        @Override
        public void post(String url, String json, JsonHttpResponseHandler responseHandler) {
            Log.d(TAG, "post=" + url + ", json=" + json);
            ByteArrayEntity entity = new ByteArrayEntity(json.getBytes());
            mClient.post(mContext, url, entity, "application/json", responseHandler);
        }

        @Override
        public void put(String url, RequestParams params, JsonHttpResponseHandler responseHandler) {
            Log.d(TAG, "put="+url+", params="+params);
            mClient.put(url, params, responseHandler);
        }

        @Override
        public void delete(String url, JsonHttpResponseHandler responseHandler) {
            Log.d(TAG, "delete="+url);
            mClient.delete(url, responseHandler);
        }
    };
}