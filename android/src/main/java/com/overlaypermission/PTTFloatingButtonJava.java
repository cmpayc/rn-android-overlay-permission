package com.overlaypermission;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;

import java.util.concurrent.TimeUnit;

import android.media.AudioManager;

class PTTFloatingButtonJava {
    // private Context context;
    private ReactApplicationContext context;
    private View floatingView = null;
    private WindowManager windowManager;
    private WindowManager.LayoutParams floatingViewLP;

    private ImageView ivTalk = null;
    private TextView tvStatus= null;
    private Button btnCancel = null;
    private RelativeLayout rlControlsContainer = null;

    private AudioManager audioManager;
    private Thread audioThread = null;

    private int lastY = 0;

    private View.OnTouchListener talkBtnTouchListener = new TalkBtnTouchListener();

    public PTTFloatingButtonJava(ReactApplicationContext context) {
        this.context = context;
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        audioManager = ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE));
    }

    public void enableOverlay() {
        if (floatingView != null) {
            removeOverlay();
        }
        floatingView = LayoutInflater.from(context).inflate(R.layout.ptt_floating_button, null, false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            floatingViewLP = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
        } else {
            floatingViewLP = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
        }

        ivTalk = floatingView.findViewById(R.id.iv_talk);
        tvStatus = floatingView.findViewById(R.id.tv_status);
        btnCancel = floatingView.findViewById(R.id.btn_cancel);
        rlControlsContainer = floatingView.findViewById(R.id.rl_controls_container);

        ivTalk.setOnTouchListener(talkBtnTouchListener);

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setAudioMode(audioManager.MODE_NORMAL);
                onTalkBtnReleased();
            }
        });

        Display display = windowManager.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        floatingViewLP.gravity = Gravity.END;

        if (lastY == 0) {
            floatingViewLP.y = size.y / 2;
            lastY = floatingViewLP.y;
        }
        windowManager.addView(floatingView, floatingViewLP);
    }

    public void removeOverlay() {
        windowManager.removeView(floatingView);
        floatingView = null;
    }

    public void onTalkBtnPressed() {
        WritableMap params = Arguments.createMap();
        params.putBoolean("pressed", true);
        context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("PttAction", params);
    }

    public void onTalkBtnReleased() {
        setStatus(false, "");
        WritableMap params = Arguments.createMap();
        params.putBoolean("pressed", false);
        context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("PttAction", params);
    }

    public void setStatus(Boolean active, String statusText) {
        ivTalk.setSelected(active);
        int newVisibility = statusText.isEmpty() ? View.GONE : View.VISIBLE;
        if (rlControlsContainer.getVisibility() != newVisibility) {
            rlControlsContainer.setVisibility(newVisibility);
        }
        tvStatus.setText(statusText);
    }

    public boolean isView() {
        return floatingView != null;
    }

    private void setAudioMode(int mode) {
        final int newMode = mode;
        if (newMode != audioManager.getMode()) {
            audioThread = new Thread(new Runnable() {
                public void run() {
                    audioManager.setMode(newMode);
                }
            }, "AudioManager Thread");
            audioThread.start();
        }
    }

    public class TalkBtnTouchListener implements View.OnTouchListener {

        private float initialTouchY = 0f;
        private float initialY = 0;
        private boolean wasLongPressPerformed = false;

        private Boolean isLongPressPerformed() {
            return wasLongPressPerformed;
        }

        private Disposable touchDisposable = null;

        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            switch (motionEvent.getAction()) {
                case (MotionEvent.ACTION_DOWN): {
                    if (floatingViewLP != null) {
                        setAudioMode(audioManager.MODE_IN_COMMUNICATION);
                        initialY = floatingViewLP.y;
                        initialTouchY = motionEvent.getRawY();
                        if (view.isSelected()) {
                            touchDisposable = Observable.timer(400, TimeUnit.MILLISECONDS)
                                    .subscribeOn(AndroidSchedulers.mainThread())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .map(new Function<Long, Boolean>() {
                                        @Override
                                        public Boolean apply(Long str) throws Exception {
                                            wasLongPressPerformed = true;
                                            onTalkBtnPressed();
                                            return true;
                                        }
                                    })
                                    .subscribeOn(AndroidSchedulers.mainThread())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe();
                        }
                    }
                    return true;
                }
                case (MotionEvent.ACTION_UP): {
                    setAudioMode(audioManager.MODE_NORMAL);
                    if (touchDisposable != null) {
                        touchDisposable.dispose();
                        if (wasLongPressPerformed) {
                            onTalkBtnReleased();
                        }
                        wasLongPressPerformed = false;
                    }
                    return true;
                }
                case (MotionEvent.ACTION_MOVE): {
                    if (floatingViewLP != null) {
                        floatingViewLP.y = Math.round(motionEvent.getRawY() - initialTouchY + initialY);
                        lastY = floatingViewLP.y;
                        windowManager.updateViewLayout(floatingView, floatingViewLP);
                    }
                    return true;
                }
            }
            return true;
        }


    };
}