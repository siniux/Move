package pt.flick.move;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.service.wallpaper.WallpaperService;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.NotificationCompat;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.ActivityRecognition;

public class MoveWallpaper extends WallpaperService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    //Gestão das Cores
    ValueAnimator colorAnimation;

    long duration = 3600000;
    //long duration = 30000;

    long tempDuration = duration;
    long walkDuration;

    int tempColor;

    int tempReverseEndColor;

    private void startAnimator() {
        int startColor = getApplicationContext().getResources().getColor(R.color.startColor);
        int endColor = getApplicationContext().getResources().getColor(R.color.endColor);

        try {
            if (colorAnimation.isRunning()) {
                tempColor = (int) colorAnimation.getAnimatedValue();
                colorAnimation.end();
            }
        } catch (NullPointerException e) {
            tempColor = getApplicationContext().getResources().getColor(R.color.startColor);
        }

        colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), tempColor, endColor);
        colorAnimation.setDuration(tempDuration);

        colorAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // done
                //Por a vibrar aqui
                Log.d("MOVE", "acabei");

                NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext());
                builder.setContentText( "Time to MOVE!" );
                builder.setSmallIcon( R.mipmap.ic_launcher );
                builder.setContentTitle( getString( R.string.app_name ) );
                NotificationManagerCompat.from(getApplicationContext()).notify(0, builder.build());
            }
        });

        colorAnimation.start();
    }

    private void revertAnimator() {

        try {
            if (colorAnimation.isRunning()) {
                tempColor = (int) colorAnimation.getAnimatedValue();
                colorAnimation.end();
            }
        } catch (NullPointerException e) {
            tempColor = getApplicationContext().getResources().getColor(R.color.startColor);
        }

        colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), tempColor, tempReverseEndColor);
        colorAnimation.setDuration(1000);

        colorAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // done
                //Por a vibrar aqui
                Log.d("MOVE", "acabei - Revert");
                tempColor = (int) colorAnimation.getAnimatedValue();

                Toast.makeText(getApplicationContext(), "A cor temp no fim é: " + Integer.toString(tempColor), Toast.LENGTH_SHORT).show();

                startAnimator();
            }
        });

        colorAnimation.start();
    }

    private void walkAnimator(long time) {
        Log.d("MOVE", "walkanimator");

        walkDuration = time;

        tempDuration = duration - colorAnimation.getCurrentPlayTime() + ( (long) (time * 100 / 3.125));

        colorAnimation.setCurrentPlayTime(tempDuration);

        tempReverseEndColor = (int) colorAnimation.getAnimatedValue();

        colorAnimation.pause();

        revertAnimator();

    }

    private int getAnimationColor() {
        int currentColor;

        if (colorAnimation.isRunning()) {
            currentColor = (int) colorAnimation.getAnimatedValue();
        } else {
            currentColor = getResources().getColor(R.color.endColor);
        }

        return currentColor;
    }

    //Gestão da Actividade
    public GoogleApiClient mApiClient;

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d("ActivityRecogition", "connected");
        Intent intent = new Intent( this, ActivityRecognizedService.class );
        PendingIntent pendingIntent = PendingIntent.getService( this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT );
        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates( mApiClient, 1000, pendingIntent );

        Toast.makeText(getApplicationContext(), "connected"
                , Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    //Gestão do Wallpaper
    private final int frameDuration = 20;

    @Override
    public Engine onCreateEngine() {
        mApiClient = new GoogleApiClient.Builder(this)
                .addApi(ActivityRecognition.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        mApiClient.connect();

        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
                new IntentFilter("ActivityRecognized"));

        return new MoveEngine();
    }

    long startWalkTime;
    long endWalkTime;

    boolean walked = false;

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent
            String message = intent.getStringExtra("message");
            Log.d("MOVE - receiver", "Got message: " + message);

            if (message.equals("walking")) {
                if (!walked) {
                    Toast.makeText(getApplicationContext(), "Walking", Toast.LENGTH_SHORT).show();

                    walked = true;

                    Time time = new Time();
                    time.setToNow();

                    startWalkTime = time.toMillis(false);
                }
            } else if (message.equals("still")) {

                if (walked) {
                    Toast.makeText(getApplicationContext(), "Stilling" , Toast.LENGTH_SHORT).show();

                    walked = false;

                    Time time = new Time();
                    time.setToNow();

                    endWalkTime = time.toMillis(false);

                    walkAnimator(endWalkTime - startWalkTime);

                    Toast.makeText(getApplicationContext(), "Doing Reverse - " + Long.toString(endWalkTime - startWalkTime), Toast.LENGTH_SHORT).show();
                }

            }

        }
    };


    private class MoveEngine extends Engine {
        private final Handler handler = new Handler();

        private final Runnable drawRunner = new Runnable() {
            @Override
            public void run() {
                draw();
            }

        };

        private Paint paint = new Paint();

        private boolean visible = true;

        public MoveEngine() {
            startAnimator();
            paint.setAntiAlias(true);
            paint.setColor(Color.WHITE);
            handler.post(drawRunner);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            this.visible = visible;
            if (visible) {
                handler.post(drawRunner);
            } else {
                handler.removeCallbacks(drawRunner);
            }
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            this.visible = false;
            handler.removeCallbacks(drawRunner);
        }


        private void draw() {
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas != null) {
                    canvas.drawColor(getAnimationColor());
                }
            } finally {
                if (canvas != null)
                    holder.unlockCanvasAndPost(canvas);
            }
            handler.removeCallbacks(drawRunner);
            if (visible) {
                handler.postDelayed(drawRunner, frameDuration);
            }

        }


    }
}
