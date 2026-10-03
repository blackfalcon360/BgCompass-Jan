package bgcompass.blackfalcon.jan;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
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

/** Pick your background image, place the direction text, and set the live wallpaper. */
public class SettingsActivity extends Activity {

    private static final int REQ_PICK = 1;
    private static final int GOLD = 0xFFC9A227;
    private static final int POS_MIN = 40; // % of screen height

    private SharedPreferences prefs;
    private ImageView preview;
    private TextView posLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(CompassWallpaperService.PREFS, MODE_PRIVATE);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF101216);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setPadding(dp(24), dp(56), dp(24), dp(32));

        column.addView(label("BgCompass", 30, 0xFFFFFFFF, true));
        column.addView(label("Live compass wallpaper.\nDegrees and direction in one line (e.g. 245\u00B0 SW) just above the fingerprint.",
                15, 0xFF9AA0A6, false));

        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackgroundColor(0xFF1B1F27);
        column.addView(preview, new LinearLayout.LayoutParams(dp(150), dp(300)));

        column.addView(button("Choose background image", v -> pickImage()), buttonParams());
        column.addView(button("Remove image", v -> removeImage()), buttonParams());

        int pos = prefs.getInt(CompassWallpaperService.KEY_DIR_POS, CompassWallpaperService.DEFAULT_DIR_POS);
        posLabel = label("", 14, GOLD, false);
        posLabel.setPadding(0, dp(20), 0, dp(4));
        posLabel.setText("Text position: " + pos + "%  (just above your fingerprint)");
        column.addView(posLabel);

        SeekBar seek = new SeekBar(this);
        seek.setMax(90 - POS_MIN);
        seek.setProgress(pos - POS_MIN);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int value = progress + POS_MIN;
                posLabel.setText("Text position: " + value + "%  (just above your fingerprint)");
                prefs.edit().putInt(CompassWallpaperService.KEY_DIR_POS, value).apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        column.addView(seek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        column.addView(button("Set as live wallpaper", v -> setWallpaper()), buttonParams());

        scroll.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView credit = label("By: Black Falcon", 14, GOLD, true);
        credit.setPadding(0, 0, 0, 0);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        lp.setMargins(0, dp(16), dp(20), 0);
        root.addView(credit, lp);

        setContentView(root);
        refreshPreview();
    }

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        startActivityForResult(Intent.createChooser(i, "Choose background image"), REQ_PICK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK && resultCode == RESULT_OK && data != null && data.getData() != null) {
            importImage(data.getData());
        }
    }

    /** Copies the chosen image into the app (sized for this screen) so the wallpaper can use it. */
    private void importImage(final Uri uri) {
        final DisplayMetrics dm = getResources().getDisplayMetrics();
        new Thread(() -> {
            boolean ok = false;
            try {
                int target = Math.max(dm.widthPixels, dm.heightPixels);

                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    BitmapFactory.decodeStream(in, null, bounds);
                }
                int sample = 1;
                while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) {
                    sample *= 2;
                }

                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = sample;
                Bitmap bmp;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    bmp = BitmapFactory.decodeStream(in, null, opts);
                }

                if (bmp != null) {
                    int rotation = 0;
                    try (InputStream in = getContentResolver().openInputStream(uri)) {
                        ExifInterface exif = new ExifInterface(in);
                        int o = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_NORMAL);
                        if (o == ExifInterface.ORIENTATION_ROTATE_90) rotation = 90;
                        else if (o == ExifInterface.ORIENTATION_ROTATE_180) rotation = 180;
                        else if (o == ExifInterface.ORIENTATION_ROTATE_270) rotation = 270;
                    } catch (Exception ignored) {
                    }
                    if (rotation != 0) {
                        Matrix m = new Matrix();
                        m.postRotate(rotation);
                        Bitmap rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
                        bmp.recycle();
                        bmp = rotated;
                    }
                    try (FileOutputStream out = new FileOutputStream(
                            new File(getFilesDir(), CompassWallpaperService.BG_FILE))) {
                        ok = bmp.compress(Bitmap.CompressFormat.JPEG, 92, out);
                    }
                    bmp.recycle();
                }
            } catch (Throwable t) {
                ok = false;
            }
            final boolean done = ok;
            runOnUiThread(() -> {
                if (done) {
                    markBackgroundChanged();
                    Toast.makeText(this, "Background saved", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Could not load that image", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void removeImage() {
        File f = new File(getFilesDir(), CompassWallpaperService.BG_FILE);
        if (f.exists()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
        markBackgroundChanged();
    }

    private void markBackgroundChanged() {
        prefs.edit().putLong(CompassWallpaperService.KEY_BG_VERSION, System.currentTimeMillis()).apply();
        refreshPreview();
    }

    private void refreshPreview() {
        File f = new File(getFilesDir(), CompassWallpaperService.BG_FILE);
        if (f.exists()) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 4;
            preview.setImageBitmap(BitmapFactory.decodeFile(f.getPath(), o));
        } else {
            preview.setImageDrawable(null);
        }
    }

    private void setWallpaper() {
        try {
            Intent i = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            i.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    new ComponentName(this, CompassWallpaperService.class));
            startActivity(i);
        } catch (Exception e) {
            try {
                startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
            } catch (Exception e2) {
                Toast.makeText(this, "Open Settings > Wallpaper > Live wallpapers and pick BgCompass",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private Button button(String text, android.view.View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(l);
        return b;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        return lp;
    }

    private TextView label(String text, float sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, 0, 0, dp(16));
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
