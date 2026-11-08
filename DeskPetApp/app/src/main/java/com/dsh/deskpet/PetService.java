package com.dsh.deskpet;

import android.animation.ObjectAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * 悬浮桌宠：把导入的图片画成一个可拖动、可调大小 / 透明度 / 圆角的悬浮窗口。
 */
public class PetService extends Service {

    public static final String ACTION_START = "com.dsh.deskpet.action.START";
    public static final String ACTION_UPDATE = "com.dsh.deskpet.action.UPDATE";
    public static final String ACTION_STOP = "com.dsh.deskpet.action.STOP";

    private static final String CHANNEL_ID = "pet_overlay";
    private static final int NOTIFICATION_ID = 0x5045;
    private static final int FGS_TYPE_SPECIAL_USE = 0x40000000;

    private static PetService sInstance;

    private WindowManager windowManager;
    private FrameLayout petView;
    private ImageView petImage;
    private WindowManager.LayoutParams params;
    private SharedPreferences prefs;

    private float cornerRadiusPx;
    private int touchSlop;
    private float downX;
    private float downY;
    private int startX;
    private int startY;
    private boolean dragging;

    public static boolean isRunning() {
        return sInstance != null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        prefs = PetPrefs.of(this);
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        startForegroundCompat();
        showPet();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_UPDATE.equals(action)) {
            applySettings();
        } else {
            showPet();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        sInstance = null;
        removePet();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---------------------------------------------------------------- 窗口

    private void showPet() {
        if (petView != null) {
            applySettings();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        petView = new FrameLayout(this);
        petImage = new ImageView(this);
        petImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        petView.addView(petImage, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        petView.setOutlineProvider(new RoundOutlineProvider());
        petView.setClipToOutline(true);

        params = new WindowManager.LayoutParams();
        params.type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        params.format = PixelFormat.TRANSLUCENT;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        params.gravity = Gravity.TOP | Gravity.START;
        params.width = dp(PetPrefs.SIZE_DEFAULT);
        params.height = dp(PetPrefs.SIZE_DEFAULT);
        params.x = dp(24);
        params.y = dp(240);

        petView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dragging = false;
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startX = params.x;
                        startY = params.y;
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float dx = event.getRawX() - downX;
                        float dy = event.getRawY() - downY;
                        if (!dragging && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                            dragging = true;
                        }
                        if (dragging) {
                            params.x = startX + (int) dx;
                            params.y = startY + (int) dy;
                            safeUpdate();
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (!dragging) {
                            bounce();
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });

        try {
            windowManager.addView(petView, params);
        } catch (Throwable t) {
            petView = null;
            stopSelf();
            return;
        }
        applySettings();
    }

    private void safeUpdate() {
        if (petView == null) {
            return;
        }
        try {
            windowManager.updateViewLayout(petView, params);
        } catch (Throwable ignored) {
        }
    }

    private void removePet() {
        if (petView != null) {
            try {
                windowManager.removeView(petView);
            } catch (Throwable ignored) {
            }
            petView = null;
            petImage = null;
        }
    }

    private void applySettings() {
        if (petView == null) {
            return;
        }
        int size = clamp(prefs.getInt(PetPrefs.KEY_SIZE, PetPrefs.SIZE_DEFAULT),
                PetPrefs.SIZE_MIN, PetPrefs.SIZE_MAX);
        int alpha = clamp(prefs.getInt(PetPrefs.KEY_ALPHA, PetPrefs.ALPHA_DEFAULT), 20, 100);
        int corner = clamp(prefs.getInt(PetPrefs.KEY_CORNER, PetPrefs.CORNER_DEFAULT), 0, 100);

        params.width = dp(size);
        params.height = dp(size);
        cornerRadiusPx = dp(size) / 2f * (corner / 100f);
        petView.setAlpha(alpha / 100f);
        petView.invalidateOutline();

        petImage.setImageBitmap(loadBitmap());
        safeUpdate();
    }

    private Bitmap loadBitmap() {
        if (!PetPrefs.hasImage(this)) {
            return null;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeFile(PetPrefs.imageFile(this).getAbsolutePath(), opts);
    }

    private void bounce() {
        if (petImage == null) {
            return;
        }
        animateScale(1f, 0.86f, 90, 0);
        animateScale(0.86f, 1f, 160, 90);
    }

    private void animateScale(float from, float to, long duration, long delay) {
        if (petImage == null) {
            return;
        }
        ObjectAnimator sx = ObjectAnimator.ofFloat(petImage, View.SCALE_X, from, to);
        ObjectAnimator sy = ObjectAnimator.ofFloat(petImage, View.SCALE_Y, from, to);
        sx.setDuration(duration);
        sy.setDuration(duration);
        sx.setStartDelay(delay);
        sy.setStartDelay(delay);
        sx.start();
        sy.start();
    }

    // ------------------------------------------------------------ 前台通知

    private void startForegroundCompat() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "桌宠悬浮窗", NotificationManager.IMPORTANCE_MIN);
            channel.setShowBadge(false);
            nm.createNotificationChannel(channel);
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | pendingFlag());
        builder.setSmallIcon(android.R.drawable.ic_menu_gallery)
                .setContentTitle("桌宠运行中")
                .setContentText("点击打开设置，可拖到任意位置")
                .setContentIntent(pi)
                .setOngoing(true);

        Intent stop = new Intent(this, PetService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | pendingFlag());
        builder.addAction(new Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel, "关闭", stopPi).build());

        Notification n = builder.build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, FGS_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
    }

    private static int pendingFlag() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE : 0;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /** 按当前圆角半径裁剪的 Outline。 */
    private final class RoundOutlineProvider extends ViewOutlineProvider {
        @Override
        public void getOutline(View view, Outline outline) {
            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadiusPx);
        }
    }
}
