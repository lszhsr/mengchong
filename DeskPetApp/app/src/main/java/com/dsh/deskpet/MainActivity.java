package com.dsh.deskpet;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Random;

public class MainActivity extends Activity {

    private static final int REQ_PICK_IMAGE = 1001;
    private static final int REQ_NOTIFICATION = 1002;

    private static final String[] REPLIES = {
            "汪！我在桌面上陪着你呢～",
            "今天也要好好休息哦。",
            "摸摸头，心情 +1。",
            "我在！随时可以把我拖到屏幕角落。",
            "要不要去「功能」页把我调小一点？",
            "嘿嘿，被你发现了。"
    };

    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private FrameLayout content;
    private View pageImage;
    private View pageFunc;
    private View pageChat;
    private TextView[] tabs;

    private ImageView preview;
    private TextView imageHint;
    private TextView overlayStatus;
    private CheckBox showSwitch;
    private TextView sizeValue;
    private TextView alphaValue;
    private TextView cornerValue;

    private LinearLayout chatLog;
    private ScrollView chatScroll;
    private EditText chatInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = PetPrefs.of(this);
        buildUi();
        selectTab(0);
        refreshImageState();
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIFICATION);
            }
        }
    }

    // ------------------------------------------------------------------ UI

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F2FAFC"));

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(20f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.parseColor("#14586B"));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(14), 0, dp(14));
        title.setBackgroundColor(Color.parseColor("#D8F1F7"));
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content = new FrameLayout(this);
        LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(content, contentLp);

        pageImage = buildImagePage();
        pageFunc = buildFuncPage();
        pageChat = buildChatPage();
        content.addView(pageImage);
        content.addView(pageFunc);
        content.addView(pageChat);

        LinearLayout tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setBackgroundColor(Color.parseColor("#E6F7FB"));
        tabs = new TextView[3];
        String[] names = {"图片", "功能", "对话"};
        for (int i = 0; i < names.length; i++) {
            TextView tab = new TextView(this);
            tab.setText(names[i]);
            tab.setTextSize(15f);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(0, dp(16), 0, dp(16));
            final int index = i;
            tab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectTab(index);
                    if (index == 1) {
                        refreshFuncState();
                    }
                }
            });
            tabBar.addView(tab, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            tabs[i] = tab;
        }
        root.addView(tabBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    private void selectTab(int index) {
        View[] pages = {pageImage, pageFunc, pageChat};
        for (int i = 0; i < pages.length; i++) {
            pages[i].setVisibility(i == index ? View.VISIBLE : View.GONE);
            tabs[i].setTextColor(Color.parseColor(i == index ? "#0E7490" : "#7A9AA5"));
            tabs[i].setTypeface(i == index ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
    }

    private View buildImagePage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(24), dp(28), dp(24), dp(24));

        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackgroundColor(Color.parseColor("#CDEFF5"));
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(dp(200), dp(200));
        page.addView(preview, previewLp);
        preview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (PetPrefs.hasImage(MainActivity.this)) {
                    showLargeImage();
                }
            }
        });
        preview.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (PetPrefs.hasImage(MainActivity.this)) {
                    deleteImage();
                    return true;
                }
                return false;
            }
        });

        imageHint = new TextView(this);
        imageHint.setTextSize(14f);
        imageHint.setTextColor(Color.parseColor("#37505C"));
        imageHint.setGravity(Gravity.CENTER);
        imageHint.setPadding(0, dp(18), 0, dp(22));
        page.addView(imageHint);

        Button pick = new Button(this);
        pick.setText("导入图片");
        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickImage();
            }
        });
        page.addView(pick, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button remove = new Button(this);
        remove.setText("删除图片");
        remove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteImage();
            }
        });
        LinearLayout.LayoutParams removeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        removeLp.topMargin = dp(10);
        page.addView(remove, removeLp);

        return page;
    }

    private View buildFuncPage() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(20), dp(24), dp(28));
        scroll.addView(page);

        overlayStatus = new TextView(this);
        overlayStatus.setTextSize(13f);
        overlayStatus.setTextColor(Color.parseColor("#37505C"));
        page.addView(overlayStatus);

        Button askOverlay = new Button(this);
        askOverlay.setText("开启悬浮窗");
        askOverlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (canOverlay()) {
                    startPet();
                } else {
                    Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                    toast("打开权限后，回来点一次「开启悬浮窗」");
                }
            }
        });
        page.addView(askOverlay);

        showSwitch = new CheckBox(this);
        showSwitch.setText("显示桌宠");
        showSwitch.setTextSize(15f);
        showSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (showSwitch.isChecked()) {
                    if (!canOverlay()) {
                        showSwitch.setChecked(false);
                        toast("请先开启悬浮窗权限");
                        return;
                    }
                    startPet();
                } else {
                    stopPet();
                }
            }
        });
        page.addView(showSwitch);

        sizeValue = addSlider(page, "桌宠大小",
                PetPrefs.SIZE_MIN, PetPrefs.SIZE_MAX, 10,
                prefs.getInt(PetPrefs.KEY_SIZE, PetPrefs.SIZE_DEFAULT));
        alphaValue = addSlider(page, "桌宠透明度",
                20, 100, 5,
                prefs.getInt(PetPrefs.KEY_ALPHA, PetPrefs.ALPHA_DEFAULT));
        cornerValue = addSlider(page, "桌宠圆角",
                0, 100, 5,
                prefs.getInt(PetPrefs.KEY_CORNER, PetPrefs.CORNER_DEFAULT));

        Button stop = new Button(this);
        stop.setText("关闭桌宠");
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopPet();
            }
        });
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stopLp.topMargin = dp(18);
        page.addView(stop, stopLp);

        return scroll;
    }

    /** 加一个带标题和数值的滑杆，返回显示数值的 TextView。 */
    private TextView addSlider(LinearLayout parent, String label, int min, int max,
                               final int step, final int initial) {
        final String key;
        if ("桌宠大小".equals(label)) {
            key = PetPrefs.KEY_SIZE;
        } else if ("桌宠透明度".equals(label)) {
            key = PetPrefs.KEY_ALPHA;
        } else {
            key = PetPrefs.KEY_CORNER;
        }

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(0, dp(18), 0, dp(4));

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(15f);
        name.setTextColor(Color.parseColor("#14586B"));
        header.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView value = new TextView(this);
        value.setText(String.valueOf(initial));
        value.setTextSize(15f);
        value.setTextColor(Color.parseColor("#0E7490"));
        value.setGravity(Gravity.END);
        header.addView(value);
        parent.addView(header);

        SeekBar bar = new SeekBar(this);
        bar.setMax((max - min) / step);
        bar.setProgress((initial - min) / step);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int v = min + progress * step;
                if (v < min) {
                    v = min;
                }
                value.setText(String.valueOf(v));
                prefs.edit().putInt(key, v).apply();
                if (fromUser) {
                    updatePet();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                updatePet();
            }
        });
        parent.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return value;
    }

    private View buildChatPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(12), dp(16), dp(12));

        chatScroll = new ScrollView(this);
        chatLog = new LinearLayout(this);
        chatLog.setOrientation(LinearLayout.VERTICAL);
        chatScroll.addView(chatLog);
        page.addView(chatScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setPadding(0, dp(8), 0, 0);

        chatInput = new EditText(this);
        chatInput.setHint("和桌宠说点什么…");
        chatInput.setSingleLine(true);
        inputRow.addView(chatInput, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button send = new Button(this);
        send.setText("发送");
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendChat();
            }
        });
        inputRow.addView(send);
        page.addView(inputRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        appendBubble("你好，我是你的桌面宠物。导入一张图片，我就会出现到桌面上。", false);
        return page;
    }

    // ------------------------------------------------------------- 图片相关

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQ_PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) {
                return;
            }
            try {
                saveImage(uri);
                refreshImageState();
                updatePet();
                toast("已导入图片");
            } catch (Throwable t) {
                toast("导入失败：" + t.getMessage());
            }
        }
    }

    /** 把选中的图片缩放后保存到应用私有目录（避免占内存 / 权限失效）。 */
    private void saveImage(Uri uri) throws Exception {
        InputStream in = getContentResolver().openInputStream(uri);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(in, null, bounds);
        if (in != null) {
            in.close();
        }
        int max = 1080;
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= max || bounds.outHeight / (sample * 2) >= max) {
            sample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        in = getContentResolver().openInputStream(uri);
        Bitmap bitmap = BitmapFactory.decodeStream(in, null, opts);
        if (in != null) {
            in.close();
        }
        if (bitmap == null) {
            throw new IllegalStateException("无法解码这张图片");
        }
        File target = PetPrefs.imageFile(this);
        FileOutputStream out = new FileOutputStream(target);
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        out.flush();
        out.close();
        bitmap.recycle();
    }

    private void deleteImage() {
        File f = PetPrefs.imageFile(this);
        if (f.exists() && f.delete()) {
            refreshImageState();
            updatePet();
            toast("已删除该图片");
        }
    }

    private void refreshImageState() {
        File f = PetPrefs.imageFile(this);
        if (f.exists()) {
            Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
            preview.setImageBitmap(bmp);
            imageHint.setText("已导入图片（点击查看大图，长按缩略图可删除）");
        } else {
            preview.setImageDrawable(null);
            imageHint.setText("尚未导入图片，请先导入一张图片");
        }
    }

    private void showLargeImage() {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        ImageView big = new ImageView(this);
        big.setScaleType(ImageView.ScaleType.FIT_CENTER);
        big.setImageBitmap(BitmapFactory.decodeFile(
                PetPrefs.imageFile(this).getAbsolutePath()));
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
        }
        big.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        dialog.setContentView(big);
        dialog.show();
    }

    // ---------------------------------------------------------- 桌宠控制

    private boolean canOverlay() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private void startPet() {
        if (!canOverlay()) {
            toast("请先授予「显示在其他应用上层」权限");
            return;
        }
        if (!PetPrefs.hasImage(this)) {
            toast("还没有图片，先到「图片」页导入一张");
            selectTab(0);
            return;
        }
        prefs.edit().putBoolean(PetPrefs.KEY_VISIBLE, true).apply();
        Intent intent = new Intent(this, PetService.class).setAction(PetService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        refreshFuncState();
    }

    private void updatePet() {
        if (PetPrefs.hasImage(this)) {
            if (PetService.isRunning()) {
                startService(new Intent(this, PetService.class)
                        .setAction(PetService.ACTION_UPDATE));
            } else if (prefs.getBoolean(PetPrefs.KEY_VISIBLE, false) && canOverlay()) {
                startPet();
            }
        }
    }

    private void stopPet() {
        prefs.edit().putBoolean(PetPrefs.KEY_VISIBLE, false).apply();
        if (PetService.isRunning()) {
            Intent intent = new Intent(this, PetService.class).setAction(PetService.ACTION_STOP);
            startService(intent);
        }
        refreshFuncState();
    }

    private void refreshFuncState() {
        boolean granted = canOverlay();
        overlayStatus.setText(granted
                ? "悬浮窗权限：已开启"
                : "悬浮窗权限：未开启（点击「开启悬浮窗」会跳到系统设置）");
        showSwitch.setChecked(PetService.isRunning());
    }

    // ------------------------------------------------------------- 对话页

    private void sendChat() {
        String text = chatInput.getText().toString().trim();
        if (TextUtils.isEmpty(text)) {
            return;
        }
        appendBubble(text, true);
        chatInput.setText("");
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                appendBubble(REPLIES[random.nextInt(REPLIES.length)], false);
            }
        }, 550);
    }

    private void appendBubble(String text, boolean fromMe) {
        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextSize(15f);
        bubble.setTextColor(fromMe ? Color.WHITE : Color.parseColor("#1B2A33"));
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
        bubble.setBackgroundColor(fromMe
                ? Color.parseColor("#38BDF8") : Color.parseColor("#E3F2F7"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = fromMe ? Gravity.END : Gravity.START;
        lp.topMargin = dp(6);
        chatLog.addView(bubble, lp);
        chatScroll.post(new Runnable() {
            @Override
            public void run() {
                chatScroll.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pageFunc.getVisibility() == View.VISIBLE) {
            refreshFuncState();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}
