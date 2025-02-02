package com.childmonitorai;

import android.database.ContentObserver;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public abstract class BaseContentObserver extends ContentObserver {
    private static final String TAG = "BaseContentObserver";
    private Context context;

    public BaseContentObserver(Context context) {
        super(new Handler(Looper.getMainLooper()));
        this.context = context;
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        super.onChange(selfChange, uri);
        onContentChanged(uri);
    }

    // Abstract method to be implemented by subclasses
    protected abstract void onContentChanged(Uri uri);

    public void registerObserver(Uri uri) {
        context.getContentResolver().registerContentObserver(uri, true, this);
    }

    public void unregisterObserver() {
        context.getContentResolver().unregisterContentObserver(this);
    }
    
    protected Context getContext() {
        return context;
    }
}
