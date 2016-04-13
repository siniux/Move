package pt.flick.move;

import android.app.IntentService;
import android.content.Intent;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import java.util.List;

/**
 * Created by João de Lourenço on 12/04/2016.
 */
public class ActivityRecognizedService extends IntentService {

    public ActivityRecognizedService() {
        super("ActivityRecognizedService");
    }

    public ActivityRecognizedService(String name) {
        super(name);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (ActivityRecognitionResult.hasResult(intent)) {
            ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
            handleDetectedActivities(result.getProbableActivities());
        }
    }

    private void handleDetectedActivities(List<DetectedActivity> probableActivities) {

        for (DetectedActivity activity : probableActivities) {
            switch (activity.getType()) {
                case DetectedActivity.STILL: {
                    if (activity.getConfidence() >= 50) {
                        sendMessage("still");
                    }
                    break;
                }
                case DetectedActivity.WALKING: {
                    if (activity.getConfidence() >= 50) {
                        sendMessage("walking");
                    }
                    break;
                }
            }
        }
    }

    private void sendMessage(String string) {
        Log.d("sender", "Broadcasting message");
        Intent intent = new Intent("ActivityRecognized");
        // You can also include some extra data.
        intent.putExtra("message", string);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
}
