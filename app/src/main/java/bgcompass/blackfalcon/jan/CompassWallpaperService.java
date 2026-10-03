package bgcompass.blackfalcon.jan;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.hardware.SensorManager;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import java.io.File;

/**
 * Live wallpaper: your image as background and one line of text just above the
 * fingerprint area: degrees + direction, e.g. "245\u00B0 SW", in a single font size.
 */
public class CompassWallpaperService extends WallpaperService {

    static final String PREFS = "bgcompass_prefs";
    static final String KEY_BG_VERSION = "bg_version";
    static final String KEY_DIR_POS = "dir_pos";
    static final String BG_FILE = "bg.jpg";
    static final int DEFAULT_DIR_POS = 74; // % of screen height

    private static final String[] DIRS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    @Override
    public Engine onCreateEngine() {
        return new CompassEngine();
    }

    private class CompassEngine extends Engine
            implements HeadingProvider.Listener, SharedPreferences.OnSharedPreferenceChangeListener {

        private HeadingProvider provider;
        private SharedPreferences prefs;

        private int width, height;
        private boolean surfaceReady;
        private Bitmap background;
        private boolean haveHeading;
        private float heading;
        private int lastDeg = -1;
        private int dirPos = DEFAULT_DIR_POS;

        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dimPaint = new Paint();

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            dirPos = prefs.getInt(KEY_DIR_POS, DEFAULT_DIR_POS);
            prefs.registerOnSharedPreferenceChangeListener(this);

            provider = new HeadingProvider(CompassWallpaperService.this, this, 0.2);

            textPaint.setColor(0xFFFFFFFF);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setShadowLayer(10f, 0f, 2f, 0xAA000000);

            dimPaint.setColor(0x55000000); // light dark overlay so text stays readable on any image
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            super.onSurfaceChanged(holder, format, w, h);
            width = w;
            height = h;
            surfaceReady = true;
            loadBackground();
            draw();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            surfaceReady = false;
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            if (visible) {
                provider.start(SensorManager.SENSOR_DELAY_UI);
                draw();
            } else {
                provider.stop(); // no sensor use while the wallpaper is not on screen
            }
        }

        @Override
        public void onDestroy() {
            provider.stop();
            prefs.unregisterOnSharedPreferenceChangeListener(this);
            if (background != null) {
                background.recycle();
                background = null;
            }
            super.onDestroy();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
            if (KEY_BG_VERSION.equals(key)) {
                loadBackground();
            } else if (KEY_DIR_POS.equals(key)) {
                dirPos = sp.getInt(KEY_DIR_POS, DEFAULT_DIR_POS);
            }
            draw();
        }

        @Override
        public void onHeading(float degrees) {
            heading = degrees;
            haveHeading = true;
            int d = Math.round(degrees) % 360;
            if (d != lastDeg) { // redraw only when the visible number changes
                lastDeg = d;
                draw();
            }
        }

        /** Loads the saved image and crops it to fill the screen. */
        private void loadBackground() {
            if (background != null) {
                background.recycle();
                background = null;
            }
            if (width <= 0 || height <= 0) return;
            File f = new File(getFilesDir(), BG_FILE);
            if (!f.exists()) return;
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(f.getPath(), bounds);
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return;

                int sample = 1;
                while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) {
                    sample *= 2;
                }
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = sample;
                Bitmap src = BitmapFactory.decodeFile(f.getPath(), opts);
                if (src == null) return;

                Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(out);
                float scale = Math.max((float) width / src.getWidth(), (float) height / src.getHeight());
                Matrix m = new Matrix();
                m.postScale(scale, scale);
                m.postTranslate((width - src.getWidth() * scale) / 2f, (height - src.getHeight() * scale) / 2f);
                c.drawBitmap(src, m, new Paint(Paint.FILTER_BITMAP_FLAG));
                src.recycle();
                background = out;
            } catch (Throwable t) {
                background = null;
            }
        }

        private void draw() {
            if (!surfaceReady || width <= 0 || height <= 0) return;
            SurfaceHolder holder = getSurfaceHolder();
            Canvas c = null;
            try {
                c = holder.lockCanvas();
                if (c == null) return;

                if (background != null) {
                    c.drawBitmap(background, 0f, 0f, null);
                    c.drawRect(0f, 0f, width, height, dimPaint);
                } else {
                    c.drawColor(0xFF101216);
                }

                // one line, one font size: degrees + direction, just above the fingerprint
                String text = haveHeading
                        ? (Math.round(heading) % 360) + "\u00B0 " + DIRS[((int) ((heading + 22.5f) / 45f)) % 8]
                        : "--\u00B0";
                textPaint.setTextSize(width * 0.13f);
                c.drawText(text, width / 2f, height * dirPos / 100f, textPaint);
            } catch (Throwable ignored) {
                // never crash the wallpaper
            } finally {
                if (c != null) {
                    try {
                        holder.unlockCanvasAndPost(c);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }
}
