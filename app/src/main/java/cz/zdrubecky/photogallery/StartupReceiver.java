package cz.zdrubecky.photogallery;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

// The receiver dies after it handles the wake up call in onReceive()
// Works on UI thread - no network or intensive storage, no listening or async tasks, it's shortlived
public class StartupReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        boolean isOn = QueryPreferences.isAlarmOn(context);
        PollService.setServiceAlarm(context, isOn);
    }
}
