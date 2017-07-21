package cz.zdrubecky.photogallery;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.app.Fragment;
import android.widget.Toast;


public abstract class VisibleFragment extends Fragment {
    private static final String TAG = "VisibleFragment";

    @Override
    public void onStart() {
        super.onStart();

        // Create a filter just like the ones in a manifest
        IntentFilter filter = new IntentFilter(PollService.ACTION_SHOW_NOTIFICATION);

        // Register using the permission so that no one else can wake the receiver
        getActivity().registerReceiver(mOnShowNotification, filter, PollService.PERM_PRIVATE, null);
    }

    // use these corresponding lifecycle methods for safekeeping the receiver
    @Override
    public void onStop() {
        super.onStop();

        getActivity().unregisterReceiver(mOnShowNotification);
    }

    // Dynamic receiver, included in this class only
    // Used as a way to be sure the parent activity is running, which would be hard from a standalone receiver
    private BroadcastReceiver mOnShowNotification = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
//            Toast.makeText(context, "Got a broadcast " + intent.getAction(), Toast.LENGTH_SHORT).show();
            // If we received this, the notification should not be shown as the activity is in the foreground
            // This info will propagate through all the following receivers
            setResultCode(Activity.RESULT_CANCELED);
        }
    };
}
