package com.xlythe.dao;

public interface Callback<T> {
    void onSuccess(T object);
    void onFailure(Throwable throwable);
}
