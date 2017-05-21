package cz.zdrubecky.photogallery;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.util.LruCache;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

// use a generic argument to make this class more flexible (every time it's mentioned, don't forget to use it)
// Message loop = thread + Looper, which runs around and takes care of the queue (it's his inbox)
// every handler has one looper, but looper can server multiple handlers
public class ThumbnailDownloader<T> extends HandlerThread {
    private static final String TAG = "ThumbnailDownloader";
    // used to identify the message type (what)
    private static final int MESSAGE_DOWNLOAD = 0;

    // One of Looper's handlers - handles download requests and processing them and belongs to the background thread
    private Handler mRequestHandler;
    // A thread-safe hashmap, pairing the target with url string
    private ConcurrentMap<T, String> mRequestMap = new ConcurrentHashMap<>();
    // This one is a reference to the main thread's handler
    private Handler mResponseHandler;
    // This listener implements the interface needed to be informed about image download
    private ThumbnailDownloadListener<T> mThumbnailDownloadListener;
    // A memory cache where the bitmaps are gonna be stored
    private LruCache<String, Bitmap> mCache;

    // The interface through which to communicate with the parent UI thread
    public interface ThumbnailDownloadListener<T> {
        // This method separates the downloading of image with its rendering, delegates the work to the UI
        void onThumbnailDownloaded(T target, Bitmap thumbnail);
    }

    public void setThumbnailDownloadListener(ThumbnailDownloadListener<T> listener) {
        mThumbnailDownloadListener = listener;
    }

    public ThumbnailDownloader(Handler responseHandler) {
        super(TAG);
        // Save the given handler
        mResponseHandler = responseHandler;

        // Set the memory limit for the cache
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = maxMemory / 10;
        mCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                // The cache size will be measured in kilobytes rather than number of items.
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    @Override
    protected void onLooperPrepared() {
        // Init the handler right before it visits the loop and define what it will do with the downloaded messages
        mRequestHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MESSAGE_DOWNLOAD) {
                    T target = (T) msg.obj;
                    Log.v(TAG, "Got a request for URL: " + mRequestMap.get(target));
                    handleRequest(target);
                }
            }
        };
    }

    public void queueThumbnail(T target, String url) {
        Log.v(TAG, "Got a URL: " + url);

        if (url == null ) {
            mRequestMap.remove(target);
        } else {
            // Pair the target and its url to get it later (it has to be like this to keep track of the most recent url due to views recycling)
            mRequestMap.put(target, url);
            // Using obtainMessage we can reuse messages via their pool
            // The message is attached to the handler it's been called on
            // The target (message object) is a view holder in our case and serves as an identifier
            mRequestHandler.obtainMessage(MESSAGE_DOWNLOAD, target).sendToTarget();
        }
    }

    // Remove the invalid messages in case of a rotated screen
    public void clearQueue() {
        mRequestHandler.removeMessages(MESSAGE_DOWNLOAD);
    }

    // The message has been pulled out of the queue and handled here
    private void handleRequest(final T target) {
        try {
            final String url = mRequestMap.get(target);

            if (url == null) {
                return;
            }

            final Bitmap bitmap;

            // Try to retrieve the cached image
            if (mCache.get(url) == null) {
                byte[] bitmapBytes = new FlickrFetchr().getUrlBytes(url);
                bitmap = BitmapFactory.decodeByteArray(bitmapBytes, 0, bitmapBytes.length);
                mCache.put(url, bitmap);
                Log.v(TAG, "A new bitmap created and cached");
            } else {
                bitmap = mCache.get(url);
            }

            // use the convenience method in a predefined format
            // tell the UI handler what he needs to run and he'll do it
            mResponseHandler.post(new Runnable() {
                @Override
                public void run() {
                    // Check again, the recycler may have requested another url by now before the image download was done
                    if (mRequestMap.get(target) != url) {
                        return;
                    }

                    // Clean the map, this task is done
                    mRequestMap.remove(target);
                    // Notify the listener
                    mThumbnailDownloadListener.onThumbnailDownloaded(target, bitmap);
                }
            });
        } catch (IOException ioe) {
            Log.e(TAG, "Error downloading an image.", ioe);
        }
    }
}
