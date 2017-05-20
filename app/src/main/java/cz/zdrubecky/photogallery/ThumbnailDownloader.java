package cz.zdrubecky.photogallery;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

// use a generic argument to make this class more flexible
// Message loop = thread + Looper, which runs around and takes care of the queue (it's his inbox)
// every handler has one looper, but looper can server multiple handlers
public class ThumbnailDownloader<T> extends HandlerThread {
    private static final String TAG = "ThumbnailDownloader";
    // used to identify the message type (what)
    private static final int MESSAGE_DOWNLOAD = 0;

    private Handler mRequestHandler;
    // A thread-safe hashmap
    private ConcurrentMap<T, String> mRequestMap = new ConcurrentHashMap<>();

    public ThumbnailDownloader() {
        super(TAG);
    }

    public void queueThumbnail(T target, String url) {
        Log.i(TAG, "Got a URL: " + url);

        if (url == null ) {
            mRequestMap.remove(target);
        } else {
            mRequestMap.put(target, url);
            mRequestHandler.obtainMessage(MESSAGE_DOWNLOAD, target).sendToTarget();
        }
    }
}
