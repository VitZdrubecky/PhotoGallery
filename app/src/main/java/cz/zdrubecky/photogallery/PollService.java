package cz.zdrubecky.photogallery;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import java.util.List;

// Service inherits from Context
// They respond to intents and must therefore be declared in manifest
// Service has lifecycle methods, acts just like an activity
// If not stated otherwise, all of its guts are run on UI thread
// It's main advantage is the boilerplate used for background threads that it provides
// IntentService is non-sticky and conditional - it stops when it says so and only if the last calling ID from onStartCommand lifecycle method is equal to something
// Non stickiness means that after the OS cuts it down, it disappears into the void
// Sticky has to be shut down from remotely, like a music player by a user
// a service can be bound - and then controlled directly through its methods - the only place in Android where it can be seen
// ...when it's bound remotely, it's more useful, but also a lot harder
// JobScheduler is a Lollipop addition to handle such tasks (with condition like "do something only when there's a wifi connection ready")
// Then there's a sync adapter, which specializes in syncing with some data source
public class PollService extends IntentService {
    private static final String TAG = "PollService";

    // 60 seconds polling interval for an alarm
    private static final int POLL_INTERVAL = 1000 * 60;
    // Pre-kitkat constant
//    private static final long POLL_INTERVAL = AlarmManager.INTERVAL_FIFTEEN_MINUTES;

    // Every consumer should use this method, it's clean
    public static Intent newIntent(Context context) {
        return new Intent(context, PollService.class);
    }

    public PollService() {
        super(TAG);
    }

    public static void setServiceAlarm(Context context, boolean isOn) {
        Intent i = PollService.newIntent(context);
        // This intent wakes up a poll service with the previous intent
        // The OS acts as me when sending the intent i
        PendingIntent pi = PendingIntent.getService(context, 0, i, 0);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        if (isOn) {
            // Time basis (after some time in this case), when to start, how often, what with
            // It batches the alarm with others so that the wakeup time for cpu is minimal
            // setRepeating(…) is used in API 19 and later, cause it erases the differences in exact timing
            // ELAPSED_REALTIME is relative to the last boot (including sleeps)
            // to force the wakeup, use different constant
            alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime(), POLL_INTERVAL, pi);
        } else {
            alarmManager.cancel(pi);
            pi.cancel();
        }
    }

    // Check if the intent is created using the flag
    public static boolean isServiceAlarmOn(Context context) {
        Intent i = PollService.newIntent(context);
        PendingIntent pi = PendingIntent.getService(context, 0, i, PendingIntent.FLAG_NO_CREATE);

        return pi != null;
    }

    // Intent is called command here in service
    // The commands are served in a background thread
    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (!isNetworkAvailableAndConnected()) {
            return;
        }

        String query = QueryPreferences.getStoredQuery(this);
        String lastResultId = QueryPreferences.getLastResultId(this);
        int currentPage = QueryPreferences.getCurrentPage(this);
        List<GalleryItem> items;

        if (query == null) {
            items = new FlickrFetchr().fetchRecentPhotos(currentPage);
        } else {
            items = new FlickrFetchr().searchPhotos(query, currentPage);
        }

        if (items.size() == 0) {
            return;
        }

        String resultId = items.get(0).getId();
        if (resultId.equals(lastResultId)) {
            Log.i(TAG, "Got an old result: " + resultId);
        } else {
            Log.i(TAG, "Got a new result: " + resultId);

            Resources resources = getResources();
            Intent i = PhotoGalleryActivity.newIntent(this);
            PendingIntent pi = PendingIntent.getActivity(this, 0, i, 0);

            // The content methods take strings, so we must get them by their ids
            // It's easier to use stock image
            // AutoCancel removes the notification after it's been clicked
            // pending intent is fired after the click
            Notification notification = new NotificationCompat.Builder(this)
                    .setTicker(resources.getString(R.string.new_pictures_title))
                    .setSmallIcon(android.R.drawable.ic_menu_report_image)
                    .setContentTitle(resources.getString(R.string.new_pictures_title))
                    .setContentText(resources.getString(R.string.new_pictures_title))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();

            NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(this);
            // The id has to be unique, which it is for now
            notificationManagerCompat.notify(0, notification);
        }

        QueryPreferences.setLastResultId(this, resultId);
    }

    private boolean isNetworkAvailableAndConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        // The user may disable network access just for this service
        boolean isNetworkAvailable = cm.getActiveNetworkInfo() != null;
        boolean isNetworkConnected = isNetworkAvailable && cm.getActiveNetworkInfo().isConnected();

        return isNetworkConnected;
    }
}
