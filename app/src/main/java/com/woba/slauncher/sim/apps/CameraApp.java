package com.woba.slauncher.sim.apps;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaActionSound;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Size;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.OrientationEventListener;

import com.woba.slauncher.sim.SimApp;
import com.woba.slauncher.sim.SimHost;
import com.woba.slauncher.ui.Glyph;
import com.woba.slauncher.ui.Theme;
import com.woba.slauncher.ui.WpUi;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Cámara real (Camera2): vista previa, foto, vídeo, flash, zoom con pellizco, cámara frontal y trasera.
 * Guarda en DCIM/Camera (MediaStore) y se ve en la app Photos. No usa la cámara del sistema.
 *
 * Todo el control de Camera2 corre en el hilo UI (los callbacks se pasan con el Handler principal);
 * solo el guardado del JPEG usa un hilo propio.
 */
public class CameraApp extends SimApp {
    private static final int FLASH_OFF = 0, FLASH_AUTO = 1, FLASH_ON = 2;
    private static final int ST_PREVIEW = 0, ST_LOCK = 1, ST_PRECAP = 2, ST_NONPRECAP = 3, ST_TAKEN = 4;

    private final Context ctx;
    private final CameraManager cm;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private HandlerThread thread;
    private Handler bg;
    private MediaActionSound sound;
    private OrientationEventListener orient;
    private int deviceOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;

    private FitView tv;
    private Hud hud;

    // cámaras
    private String backId, frontId, camId;
    private CameraCharacteristics chars;
    private int sensorOrientation = 90;
    private boolean hasFlash, hasAf;
    private float maxZoom = 1f;

    // controles manuales de Lumia Camera (-1 = automático)
    private static final int[] WB_MODES = {CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT, CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT,
            CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT, CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT, CaptureRequest.CONTROL_AWB_MODE_SHADE};
    private static final String[] WB_NAMES = {"2700K", "4000K", "5500K", "6500K", "7500K"};
    private int wbIdx = -1;
    private float focusV = -1f, isoV = -1f, expV = -1f, evV = 0.5f;
    private int lastIso = 100;
    private long lastExp = 10_000_000L;
    private int[] awbModes;
    private android.util.Range<Integer> isoRange, evRange;
    private android.util.Range<Long> expRange;
    private float minFocus, evStep = 0.333f;
    private boolean inSettings;

    // Rich Capture (ráfaga + fusión): ver data/Enhance
    private static final int RICH_HDR = 1, RICH_NIGHT = 2;
    private final Object burstLock = new Object();
    private final ArrayList<byte[]> burstFrames = new ArrayList<>();
    private int burstExpected, burstId, burstMode;
    private volatile boolean processing;
    private boolean hasManualSensor;

    // estado de la app
    private boolean shown, hostPaused, front, video, recording;
    private int flash = FLASH_OFF;
    private float zoom = 1f;
    private long recStart;

    // estado de Camera2
    private int gen;                 // sube cada vez que se abre/cierra; descarta callbacks viejos
    private int sessGen;
    private boolean opening;
    private CameraDevice device;
    private CameraCaptureSession session;
    private CaptureRequest.Builder previewBuilder;
    private Surface previewSurface;
    private ImageReader reader;
    private MediaRecorder recorder;
    private Out recOut;
    private Size previewSize, photoSize, videoSize;
    private int state = ST_PREVIEW;

    private static final class Out {
        Uri uri;
        File file;
        ParcelFileDescriptor pfd;
        String name;
    }

    public CameraApp(Context c, SimHost host) {
        super(c, host, "camera");
        this.ctx = c;
        setBackgroundColor(0xFF000000);
        cm = (CameraManager) c.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String id : cm.getCameraIdList()) {
                Integer f = cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
                if (f == null) continue;
                if (f == CameraCharacteristics.LENS_FACING_BACK && backId == null) backId = id;
                if (f == CameraCharacteristics.LENS_FACING_FRONT && frontId == null) frontId = id;
            }
        } catch (Exception ignored) { }
        if (backId == null && frontId != null) front = true;
        orient = new OrientationEventListener(c) {
            @Override public void onOrientationChanged(int o) { deviceOrientation = o; }
        };
    }

    @Override public String appTitle() { return "camera"; }

    @Override public void onThemeChanged() { setBackgroundColor(0xFF000000); }

    // ------------------------------------------------------------------ permisos

    private boolean has(String p) { return ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED; }

    private boolean hasCameraPerm() {
        if (!has(Manifest.permission.CAMERA)) return false;
        if (Build.VERSION.SDK_INT <= 28 && !has(Manifest.permission.WRITE_EXTERNAL_STORAGE)) return false;
        return true;
    }

    private String[] neededPerms() {
        ArrayList<String> l = new ArrayList<>();
        l.add(Manifest.permission.CAMERA);
        if (Build.VERSION.SDK_INT <= 28) l.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        return l.toArray(new String[0]);
    }

    private boolean askedOnce;

    private void showPermission() {
        final Context c = getContext();
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(Theme.bg);
        col.addView(WpUi.pageHeader(c, "camera", "camera"));
        TextView t = WpUi.label(c, "Allow access to the camera to take photos and videos. They are saved to your gallery.",
                19, Theme.light, Theme.fg);
        t.setPadding(Theme.dp(18), Theme.dp(10), Theme.dp(18), Theme.dp(18));
        col.addView(t);
        TextView b = WpUi.button(c, askedOnce ? "open settings" : "allow");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Theme.dp(180), Theme.dp(46));
        lp.leftMargin = Theme.dp(18);
        col.addView(b, lp);
        b.setOnClickListener(v -> {
            if (askedOnce) {
                // Android ya no vuelve a preguntar: hay que ir a los ajustes de la app
                host.launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + ctx.getPackageName())));
                return;
            }
            askedOnce = true;
            host.requestPermissions(neededPerms(), () -> { hud = null; if (shown) onShow(); });
        });
        swapContent(col, null);
    }

    // ------------------------------------------------------------------ ciclo de vida

    @Override public void onShow() {
        shown = true;
        if (inSettings) return;
        if (!hasCameraPerm()) { showPermission(); return; }
        if (hud == null) buildCamera();
        orient.enable();
        tryOpen();
    }

    @Override public void onHide() {
        shown = false;
        orient.disable();
        closeCamera();
    }

    @Override public void onDestroy() {
        shown = false;
        orient.disable();
        closeCamera();
        stopThread();
        if (sound != null) { sound.release(); sound = null; }
    }

    /** La Activity pasa a segundo plano (otra app encima): hay que soltar la cámara. */
    @Override public void onHostPause() {
        hostPaused = true;
        if (shown) closeCamera();
    }

    @Override public void onHostResume() {
        hostPaused = false;
        if (shown && !inSettings && hasCameraPerm()) {
            if (hud == null) buildCamera();
            tryOpen();
        }
    }

    /** Teclas de volumen / cámara hacen de disparador. */
    @Override public boolean onHardwareKey(int keyCode) {
        if (!shown || hud == null) return false;
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_CAMERA) {
            long now = SystemClock.uptimeMillis();
            if (now - lastKeyShot > 700) { lastKeyShot = now; shutter(); } // ignora la repetición al mantener
            return true;
        }
        return false;
    }

    private long lastKeyShot;

    @Override public boolean onBack() {
        if (recording) { stopRecording(); return true; }
        if (inSettings) {
            inSettings = false;
            hud = null;
            onShow();
            return true;
        }
        if (hud != null && hud.collapse()) return true;
        return false;
    }

    private void startThread() {
        if (thread != null) return;
        thread = new HandlerThread("woba-camera-io");
        thread.start();
        bg = new Handler(thread.getLooper());
    }

    private void stopThread() {
        if (thread == null) return;
        thread.quitSafely();
        thread = null;
        bg = null;
    }

    // ------------------------------------------------------------------ interfaz

    private void buildCamera() {
        final Context c = getContext();
        FrameLayout root = new FrameLayout(c);
        root.setBackgroundColor(0xFF000000);
        tv = new FitView(c);
        tv.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture s, int w, int h) { tryOpen(); }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture s, int w, int h) { tv.applyTransform(); }
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture s) { closeCamera(); return true; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture s) { }
        });
        root.addView(tv, new FrameLayout.LayoutParams(-2, -2, android.view.Gravity.CENTER));
        hud = new Hud(c);
        root.addView(hud, new FrameLayout.LayoutParams(-1, -1));
        swapContent(root, null);
    }

    // ------------------------------------------------------------------ abrir / cerrar

    private void tryOpen() {
        if (!shown || hostPaused || device != null || opening || tv == null || hud == null
                || !tv.isAvailable() || !hasCameraPerm()) return;
        String id = front ? frontId : backId;
        if (id == null) id = backId != null ? backId : frontId;
        if (id == null) { hud.setMessage("No camera found"); return; }
        try {
            startThread();
            camId = id;
            chars = cm.getCameraCharacteristics(id);
            Integer so = chars.get(CameraCharacteristics.SENSOR_ORIENTATION);
            sensorOrientation = so == null ? 90 : so;
            Boolean fl = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            hasFlash = fl != null && fl;
            int[] af = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
            hasAf = af != null && contains(af, CaptureRequest.CONTROL_AF_MODE_AUTO);
            Float mz = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
            maxZoom = mz == null ? 1f : Math.min(8f, mz);
            zoom = 1f;
            if (!hasFlash) flash = FLASH_OFF;
            hasManualSensor = contains(chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES),
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR);
            awbModes = chars.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES);
            isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
            expRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
            evRange = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
            android.util.Rational st = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
            evStep = st == null ? 0.333f : st.floatValue();
            Float mf = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
            minFocus = mf == null ? 0f : mf;
            configureSizes();
            hud.setMessage(null);
            opening = true;
            final int g = ++gen;
            cm.openCamera(id, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice d) {
                    opening = false;
                    if (g != gen || !shown) { d.close(); return; }
                    device = d;
                    startPreview();
                }
                @Override public void onDisconnected(CameraDevice d) {
                    d.close();
                    if (g == gen) { device = null; opening = false; hud.setMessage("Camera disconnected"); }
                }
                @Override public void onError(CameraDevice d, int e) {
                    d.close();
                    if (g == gen) {
                        device = null;
                        opening = false;
                        hud.setMessage(e == ERROR_CAMERA_IN_USE ? "Camera is in use by another app" : "Camera error (" + e + ")");
                    }
                }
            }, ui);
        } catch (SecurityException | CameraAccessException | IllegalArgumentException e) {
            opening = false;
            hud.setMessage("Camera unavailable");
        }
    }

    private void closeCamera() {
        gen++;
        opening = false;
        ui.removeCallbacks(captureTimeout);
        if (recording) {
            try { stopRecording(); } catch (Exception ignored) { }
        }
        closeSession();
        if (device != null) { try { device.close(); } catch (Exception ignored) { } device = null; }
        if (reader != null) { try { reader.close(); } catch (Exception ignored) { } reader = null; }
        if (previewSurface != null) { previewSurface.release(); previewSurface = null; }
        state = ST_PREVIEW;
    }

    private void closeSession() {
        sessGen++;
        if (session != null) {
            try { session.close(); } catch (Exception ignored) { }
            session = null;
        }
    }

    // ------------------------------------------------------------------ tamaños

    private static boolean contains(int[] a, int v) {
        if (a == null) return false;
        for (int x : a) if (x == v) return true;
        return false;
    }

    private static final Comparator<Size> BY_AREA = (a, b) ->
            Long.compare((long) a.getWidth() * a.getHeight(), (long) b.getWidth() * b.getHeight());

    /** El mayor tamaño con proporción rw:rh y lado largo <= maxLong; si no hay, el más parecido. */
    private static Size pick(Size[] all, int rw, int rh, int maxLong) {
        if (all == null || all.length == 0) return new Size(1280, 720);
        Size best = null;
        for (Size s : all) {
            if (Math.max(s.getWidth(), s.getHeight()) > maxLong) continue;
            if ((long) s.getWidth() * rh != (long) s.getHeight() * rw) continue;
            if (best == null || BY_AREA.compare(s, best) > 0) best = s;
        }
        if (best != null) return best;
        double want = (double) rw / rh, bd = 1e9;
        for (Size s : all) {
            if (Math.max(s.getWidth(), s.getHeight()) > maxLong) continue;
            double d = Math.abs((double) s.getWidth() / s.getHeight() - want);
            if (d < bd - 1e-6 || (Math.abs(d - bd) < 1e-6 && best != null && BY_AREA.compare(s, best) > 0)) { bd = d; best = s; }
        }
        if (best == null) {
            List<Size> l = new ArrayList<>(Arrays.asList(all));
            Collections.sort(l, BY_AREA);
            best = l.get(0);
        }
        return best;
    }

    private void configureSizes() {
        StreamConfigurationMap map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) { previewSize = photoSize = videoSize = new Size(1280, 720); return; }
        Size[] jpeg = map.getOutputSizes(ImageFormat.JPEG);
        Size[] prev = map.getOutputSizes(SurfaceTexture.class);
        Size[] rec = map.getOutputSizes(MediaRecorder.class);
        // foto: lo más grande en 4:3 hasta ~4000 px (si no hay 4:3, 16:9)
        String ratio = com.woba.slauncher.data.Prefs.getStr(ctx, "cam_ratio", "16:9");
        Size p43 = "4:3".equals(ratio) ? pick(jpeg, 4, 3, 4096) : pick(jpeg, 16, 9, 4096);
        photoSize = p43;
        videoSize = pick(rec, 16, 9, "720p".equals(com.woba.slauncher.data.Prefs.getStr(ctx, "cam_vq", "1080p")) ? 1280 : 1920);
        if (video) previewSize = pick(prev, videoSize.getWidth(), videoSize.getHeight(), 1920);
        else previewSize = pick(prev, photoSize.getWidth(), photoSize.getHeight(), 1920);
        tv.setBuffer(previewSize.getWidth(), previewSize.getHeight());
    }

    // ------------------------------------------------------------------ vista previa

    private int afMode(boolean forVideo) {
        int[] af = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
        int want = forVideo ? CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO : CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE;
        if (contains(af, want)) return want;
        if (contains(af, CaptureRequest.CONTROL_AF_MODE_AUTO)) return CaptureRequest.CONTROL_AF_MODE_AUTO;
        return CaptureRequest.CONTROL_AF_MODE_OFF;
    }

    private Rect cropFor(float z) {
        Rect a = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        if (a == null) return null;
        int w = Math.round(a.width() / z), h = Math.round(a.height() / z);
        int l = a.left + (a.width() - w) / 2, t = a.top + (a.height() - h) / 2;
        return new Rect(l, t, l + w, t + h);
    }

    private void applyCommon(CaptureRequest.Builder b, boolean forVideo) {
        b.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        b.set(CaptureRequest.CONTROL_AF_MODE, afMode(forVideo));
        if (zoom > 1.01f) {
            Rect r = cropFor(zoom);
            if (r != null) b.set(CaptureRequest.SCALER_CROP_REGION, r);
        }
        if (wbIdx >= 0 && contains(awbModes, WB_MODES[wbIdx])) b.set(CaptureRequest.CONTROL_AWB_MODE, WB_MODES[wbIdx]);
        if (evRange != null && !manualAe()) b.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, evIndex());
        if (manualFocus()) {
            b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            b.set(CaptureRequest.LENS_FOCUS_DISTANCE, focusDiopters(focusV));
        }
    }

    // ---- valores de los controles manuales
    private boolean manualAe() { return (isoV >= 0 && isoRange != null) || (expV >= 0 && expRange != null); }

    private boolean manualFocus() { return focusV >= 0 && minFocus > 0; }

    private int isoValue(float v) {
        double lo = isoRange.getLower(), hi = Math.min(isoRange.getUpper(), 6400);
        return (int) Math.round(lo * Math.pow(hi / lo, 1 - v));
    }

    private long expValue(float v) {
        double lo = expRange.getLower(), hi = Math.max(lo * 2, Math.min(expRange.getUpper(), 1_000_000_000L));
        return (long) (hi * Math.pow(lo / hi, v));
    }

    private float focusDiopters(float v) { return minFocus * (1 - v); }

    private int evIndex() {
        float ev = 3f - 6f * evV;
        int i = Math.round(ev / evStep);
        return Math.max(evRange.getLower(), Math.min(evRange.getUpper(), i));
    }

    private float evValue() { return evIndex() * evStep; }

    private static String expText(long ns) {
        if (ns >= 950_000_000L) return String.format(Locale.US, "%.1fs", ns / 1e9);
        return "1/" + Math.round(1e9 / ns);
    }

    private String focusText(float v) {
        float d = focusDiopters(v);
        return d < 0.05f ? "\u221e" : String.format(Locale.US, "%.2fm", 1f / d);
    }

    /** Un anillo cambió: aplicarlo a la vista previa. */
    private void manualChanged() {
        if (session != null && state == ST_PREVIEW) pushPreview();
        if (hud != null) hud.invalidate();
    }

    /** Modo de exposición/flash. En vídeo el "flash" es la linterna. */
    private void applyFlash(CaptureRequest.Builder b, boolean still) {
        if (manualAe()) {
            b.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            b.set(CaptureRequest.SENSOR_SENSITIVITY, isoV >= 0 && isoRange != null ? isoValue(isoV)
                    : Math.max(isoRange.getLower(), Math.min(isoRange.getUpper(), lastIso)));
            b.set(CaptureRequest.SENSOR_EXPOSURE_TIME, expV >= 0 && expRange != null ? expValue(expV)
                    : Math.max(expRange.getLower(), Math.min(expRange.getUpper(), lastExp)));
            b.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
            return;
        }
        if (!hasFlash || video) {
            b.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            b.set(CaptureRequest.FLASH_MODE, hasFlash && flash == FLASH_ON ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF);
            return;
        }
        if (flash == FLASH_AUTO) b.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        else if (flash == FLASH_ON) b.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
        else b.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        if (!still) b.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
    }

    private void makeReader() {
        if (reader != null) { try { reader.close(); } catch (Exception ignored) { } reader = null; }
        startThread();
        reader = ImageReader.newInstance(photoSize.getWidth(), photoSize.getHeight(), ImageFormat.JPEG, 6);
        reader.setOnImageAvailableListener(r -> {
            Image im = null;
            byte[] data = null;
            try {
                im = r.acquireNextImage();
                if (im == null) return;
                ByteBuffer buf = im.getPlanes()[0].getBuffer();
                data = new byte[buf.remaining()];
                buf.get(data);
            } catch (Exception ignored) {
            } finally {
                if (im != null) im.close();
            }
            if (data == null) return;
            ArrayList<byte[]> ready = null;
            int mode = 0;
            boolean burst;
            synchronized (burstLock) {
                burst = burstExpected > 0;
                if (burst) {
                    burstFrames.add(data);
                    if (burstFrames.size() >= burstExpected) {
                        ready = new ArrayList<>(burstFrames);
                        mode = burstMode;
                        burstFrames.clear();
                        burstExpected = 0;
                        burstId++;
                    }
                }
            }
            if (!burst) savePhoto(data);
            else if (ready != null) processBurst(ready, mode);
        }, bg);
    }

    @SuppressWarnings("deprecation")
    private void startPreview() {
        if (device == null || tv == null || !tv.isAvailable()) return;
        closeSession();
        try {
            SurfaceTexture st = tv.getSurfaceTexture();
            st.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            if (previewSurface != null) previewSurface.release();
            previewSurface = new Surface(st);
            tv.applyTransform();

            previewBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewBuilder.addTarget(previewSurface);
            List<Surface> targets = new ArrayList<>();
            targets.add(previewSurface);
            if (!video) {
                makeReader();
                targets.add(reader.getSurface());
            }
            final int sg = sessGen;
            device.createCaptureSession(targets, new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession s) {
                    if (sg != sessGen || device == null) { s.close(); return; }
                    session = s;
                    state = ST_PREVIEW;
                    pushPreview();
                    hud.invalidate();
                }
                @Override public void onConfigureFailed(CameraCaptureSession s) {
                    if (sg == sessGen) hud.setMessage("Couldn't start the camera");
                }
            }, ui);
        } catch (Exception e) {
            hud.setMessage("Couldn't start the camera");
        }
    }

    private void pushPreview() {
        if (session == null || previewBuilder == null) return;
        try {
            applyCommon(previewBuilder, video);
            applyFlash(previewBuilder, false);
            session.setRepeatingRequest(previewBuilder.build(), previewCb, ui);
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------------ foto

    private final CameraCaptureSession.CaptureCallback previewCb = new CameraCaptureSession.CaptureCallback() {
        private void process(CaptureResult r) {
            Integer si = r.get(CaptureResult.SENSOR_SENSITIVITY);
            Long se = r.get(CaptureResult.SENSOR_EXPOSURE_TIME);
            if (si != null && !manualAe()) lastIso = si;
            if (se != null && !manualAe()) lastExp = se;
            if (state == ST_PREVIEW || state == ST_TAKEN) return;
            Integer ae = r.get(CaptureResult.CONTROL_AE_STATE);
            switch (state) {
                case ST_LOCK: {
                    Integer af = r.get(CaptureResult.CONTROL_AF_STATE);
                    if (af == null) { afterLock(ae); }
                    else if (af == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED
                            || af == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) afterLock(ae);
                    break;
                }
                case ST_PRECAP:
                    if (ae == null || ae == CaptureResult.CONTROL_AE_STATE_PRECAPTURE
                            || ae == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED) state = ST_NONPRECAP;
                    break;
                case ST_NONPRECAP:
                    if (ae == null || ae != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        state = ST_TAKEN;
                        captureStill();
                    }
                    break;
                default: break;
            }
        }
        @Override public void onCaptureProgressed(CameraCaptureSession s, CaptureRequest rq, CaptureResult pr) { process(pr); }
        @Override public void onCaptureCompleted(CameraCaptureSession s, CaptureRequest rq, TotalCaptureResult r) { process(r); }
    };

    private void afterLock(Integer ae) {
        boolean needPre = flash != FLASH_OFF && hasFlash && !manualAe();
        if (!needPre || ae == null || (ae == CaptureResult.CONTROL_AE_STATE_CONVERGED && flash == FLASH_AUTO)) {
            state = ST_TAKEN;
            captureStill();
        } else {
            runPrecapture();
        }
    }

    private final Runnable captureTimeout = new Runnable() {
        @Override public void run() {
            if (state == ST_LOCK || state == ST_PRECAP || state == ST_NONPRECAP) {
                state = ST_TAKEN;
                captureStill();
            }
        }
    };

    private void takePhoto() {
        if (session == null || device == null || state != ST_PREVIEW || processing) return;
        play(MediaActionSound.SHUTTER_CLICK);
        hud.flashAnim();
        try {
            ui.removeCallbacks(captureTimeout);
            ui.postDelayed(captureTimeout, 2500);
            if (hasAf && !manualFocus()) {
                previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
                state = ST_LOCK;
                session.capture(previewBuilder.build(), previewCb, ui);
            } else if (flash != FLASH_OFF && hasFlash && !manualAe()) {
                runPrecapture();
            } else {
                state = ST_TAKEN;
                captureStill();
            }
        } catch (Exception e) {
            state = ST_PREVIEW;
        }
    }

    private void runPrecapture() {
        try {
            previewBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            state = ST_PRECAP;
            session.capture(previewBuilder.build(), previewCb, ui);
            previewBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);
        } catch (Exception e) {
            state = ST_TAKEN;
            captureStill();
        }
    }

    private void captureStill() {
        ui.removeCallbacks(captureTimeout);
        if (device == null || session == null || reader == null) { state = ST_PREVIEW; return; }
        int rm = richMode();
        if (rm != 0 && captureRich(rm)) return;
        try {
            CaptureRequest.Builder b = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            b.addTarget(reader.getSurface());
            applyCommon(b, false);
            applyFlash(b, true);
            b.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation());
            b.set(CaptureRequest.JPEG_QUALITY, (byte) 95);
            session.stopRepeating();
            session.abortCaptures();
            session.capture(b.build(), new CameraCaptureSession.CaptureCallback() {
                @Override public void onCaptureCompleted(CameraCaptureSession s, CaptureRequest rq, TotalCaptureResult r) { unlockFocus(); }
                @Override public void onCaptureFailed(CameraCaptureSession s, CaptureRequest rq, android.hardware.camera2.CaptureFailure f) { unlockFocus(); }
            }, ui);
        } catch (Exception e) {
            unlockFocus();
        }
    }

    // ------------------------------------------------------------------ Rich Capture

    /** 0 = foto normal; si no, qué ráfaga hacer según el ajuste y la luz de la escena. */
    private int richMode() {
        if (video || flash == FLASH_ON || manualAe()) return 0;
        String m = com.woba.slauncher.data.Prefs.getStr(ctx, "cam_rich", "auto");
        if ("off".equals(m)) return 0;
        if ("hdr".equals(m)) return RICH_HDR;
        if ("night".equals(m)) return RICH_NIGHT;
        boolean dark = lastIso >= 640 || lastExp > 40_000_000L; // poca luz: promediar; con luz: fundir exposiciones
        return dark ? RICH_NIGHT : RICH_HDR;
    }

    private CaptureRequest richRequest(float ev) throws CameraAccessException {
        CaptureRequest.Builder b = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        b.addTarget(reader.getSurface());
        applyCommon(b, false);
        b.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
        if (hasManualSensor && isoRange != null && expRange != null) {
            // exposición manual alrededor de lo que el automático eligió para la vista previa
            double total = (double) lastExp * lastIso * Math.pow(2, ev);
            long tcap = Math.min(expRange.getUpper(), Math.max(lastExp, 33_000_000L));
            double t = Math.max(expRange.getLower(), Math.min(tcap, lastExp * Math.pow(2, ev)));
            double iso = Math.max(isoRange.getLower(), Math.min(isoRange.getUpper(), total / t));
            b.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            b.set(CaptureRequest.SENSOR_EXPOSURE_TIME, (long) t);
            b.set(CaptureRequest.SENSOR_SENSITIVITY, (int) Math.round(iso));
        } else if (evRange != null) {
            b.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            int i = Math.round(ev / evStep);
            b.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, Math.max(evRange.getLower(), Math.min(evRange.getUpper(), i)));
        }
        b.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation());
        b.set(CaptureRequest.JPEG_QUALITY, (byte) 95);
        return b.build();
    }

    private boolean captureRich(int mode) {
        try {
            float[] evs = mode == RICH_HDR ? new float[]{-2f, 0f, 2f} : new float[]{0f, 0f, 0f, 0f, 0f};
            ArrayList<CaptureRequest> list = new ArrayList<>();
            for (float ev : evs) list.add(richRequest(ev));
            final int id;
            synchronized (burstLock) {
                burstFrames.clear();
                burstExpected = list.size();
                burstMode = mode;
                id = ++burstId;
            }
            processing = true;
            hud.setBusy(true);
            session.stopRepeating();
            session.abortCaptures();
            session.captureBurst(list, new CameraCaptureSession.CaptureCallback() {
                @Override public void onCaptureSequenceCompleted(CameraCaptureSession s, int seq, long frame) { unlockFocus(); }
                @Override public void onCaptureSequenceAborted(CameraCaptureSession s, int seq) { unlockFocus(); }
            }, ui);
            startThread();
            // si alguna foto de la ráfaga no llega, se procesa con las que haya
            bg.postDelayed(() -> flushBurst(id), 8000);
            return true;
        } catch (Exception e) {
            synchronized (burstLock) { burstExpected = 0; burstFrames.clear(); }
            processing = false;
            if (hud != null) hud.setBusy(false);
            return false;
        }
    }

    private void flushBurst(int id) {
        ArrayList<byte[]> ready;
        int mode;
        synchronized (burstLock) {
            if (id != burstId || burstExpected == 0) return;
            ready = new ArrayList<>(burstFrames);
            mode = burstMode;
            burstFrames.clear();
            burstExpected = 0;
            burstId++;
        }
        if (ready.isEmpty()) { ui.post(() -> { processing = false; if (hud != null) hud.setBusy(false); }); return; }
        processBurst(ready, mode);
    }

    /** En el hilo de E/S: fundir la ráfaga y guardar; si algo falla se guarda una de las fotos tal cual. */
    private void processBurst(ArrayList<byte[]> frames, int mode) {
        byte[] out = null;
        try {
            Bitmap b = com.woba.slauncher.data.Enhance.process(frames, mode == RICH_HDR, 8_500_000);
            if (b != null) {
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                b.compress(Bitmap.CompressFormat.JPEG, 95, bo);
                out = bo.toByteArray();
                b.recycle();
            }
        } catch (Throwable ignored) { // incluye OutOfMemoryError
        }
        if (out == null) out = frames.get(frames.size() / 2);
        savePhoto(out);
        ui.post(() -> { processing = false; if (hud != null) hud.setBusy(false); });
    }

    private void unlockFocus() {
        try {
            if (session == null || previewBuilder == null) { state = ST_PREVIEW; return; }
            previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
            applyFlash(previewBuilder, false);
            session.capture(previewBuilder.build(), previewCb, ui);
            state = ST_PREVIEW;
            previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
            pushPreview();
        } catch (Exception e) {
            state = ST_PREVIEW;
        }
    }

    /** Orientación del JPEG / vídeo: sensor + posición física del teléfono (fórmula de la documentación). */
    private int jpegOrientation() {
        int d = deviceOrientation;
        if (d == OrientationEventListener.ORIENTATION_UNKNOWN) d = (360 - displayRotation() * 90) % 360;
        d = ((d + 45) / 90 * 90) % 360;
        if (front) d = -d;
        return (sensorOrientation + d + 360) % 360;
    }

    @SuppressWarnings("deprecation")
    private int displayRotation() {
        try {
            WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            return wm.getDefaultDisplay().getRotation();
        } catch (Exception e) {
            return Surface.ROTATION_0;
        }
    }

    private void play(int s) {
        try {
            if (sound == null) sound = new MediaActionSound();
            sound.play(s);
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------------ guardado

    private Out newOutput(boolean vid) throws Exception {
        Out o = new Out();
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        o.name = (vid ? "VID_" : "IMG_") + stamp + (vid ? ".mp4" : ".jpg");
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, o.name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, vid ? "video/mp4" : "image/jpeg");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Camera");
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            o.uri = ctx.getContentResolver().insert(vid ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    : MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (o.uri == null) throw new java.io.IOException("MediaStore refused the file");
            o.pfd = ctx.getContentResolver().openFileDescriptor(o.uri, "w");
        } else {
            @SuppressWarnings("deprecation")
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            o.file = new File(dir, o.name);
            o.uri = Uri.fromFile(o.file);
        }
        return o;
    }

    private void finishOutput(Out o, boolean ok) {
        if (o == null) return;
        try { if (o.pfd != null) o.pfd.close(); } catch (Exception ignored) { }
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                if (ok) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.MediaColumns.IS_PENDING, 0);
                    ctx.getContentResolver().update(o.uri, v, null, null);
                } else {
                    ctx.getContentResolver().delete(o.uri, null, null);
                }
            } else if (o.file != null) {
                if (ok) MediaScannerConnection.scanFile(ctx, new String[]{o.file.getAbsolutePath()}, null, null);
                else //noinspection ResultOfMethodCallIgnored
                    o.file.delete();
            }
        } catch (Exception ignored) { }
    }

    /** En el hilo de E/S. */
    private void savePhoto(byte[] data) {
        Out o = null;
        boolean ok = false;
        try {
            o = newOutput(false);
            OutputStream os = o.pfd != null ? new FileOutputStream(o.pfd.getFileDescriptor()) : new FileOutputStream(o.file);
            os.write(data);
            os.flush();
            os.close();
            ok = true;
        } catch (Exception ignored) {
        } finally {
            finishOutput(o, ok);
        }
        final Bitmap th = ok ? thumbFromJpeg(data) : null;
        final boolean fin = ok;
        ui.post(() -> {
            if (hud == null) return;
            if (fin) hud.setThumb(th); else hud.setMessage("Couldn't save the photo");
        });
    }

    private static Bitmap thumbFromJpeg(byte[] d) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(d, 0, d.length, o);
            int s = 1;
            while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= 240) s *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = s;
            Bitmap b = BitmapFactory.decodeByteArray(d, 0, d.length, o);
            if (b == null) return null;
            int deg = 0;
            try {
                int ori = new android.media.ExifInterface(new ByteArrayInputStream(d))
                        .getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
                if (ori == android.media.ExifInterface.ORIENTATION_ROTATE_90) deg = 90;
                else if (ori == android.media.ExifInterface.ORIENTATION_ROTATE_180) deg = 180;
                else if (ori == android.media.ExifInterface.ORIENTATION_ROTATE_270) deg = 270;
            } catch (Exception ignored) { }
            if (deg != 0) {
                Matrix m = new Matrix();
                m.postRotate(deg);
                b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
            }
            return b;
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------------ vídeo

    @SuppressWarnings("deprecation")
    private void startRecording() {
        if (device == null || recording || session == null) return;
        try {
            closeSession();
            recOut = newOutput(true);
            MediaRecorder mr = new MediaRecorder();
            boolean audio = has(Manifest.permission.RECORD_AUDIO);
            if (audio) mr.setAudioSource(MediaRecorder.AudioSource.MIC);
            mr.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            if (recOut.pfd != null) mr.setOutputFile(recOut.pfd.getFileDescriptor());
            else mr.setOutputFile(recOut.file.getAbsolutePath());
            mr.setVideoEncodingBitRate(10_000_000);
            mr.setVideoFrameRate(30);
            mr.setVideoSize(videoSize.getWidth(), videoSize.getHeight());
            mr.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            if (audio) {
                mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                mr.setAudioEncodingBitRate(128000);
                mr.setAudioSamplingRate(44100);
            }
            mr.setOrientationHint(jpegOrientation());
            mr.prepare();
            recorder = mr;
            final Surface rs = mr.getSurface();
            previewBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            previewBuilder.addTarget(previewSurface);
            previewBuilder.addTarget(rs);
            final int sg = sessGen;
            device.createCaptureSession(Arrays.asList(previewSurface, rs), new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession s) {
                    if (sg != sessGen || device == null || recorder == null) { s.close(); return; }
                    session = s;
                    pushPreview();
                    try {
                        play(MediaActionSound.START_VIDEO_RECORDING);
                        recorder.start();
                        recording = true;
                        recStart = SystemClock.elapsedRealtime();
                        hud.invalidate();
                    } catch (Exception e) {
                        abortRecording("Couldn't start recording");
                    }
                }
                @Override public void onConfigureFailed(CameraCaptureSession s) {
                    if (sg == sessGen) abortRecording("Couldn't start recording");
                }
            }, ui);
        } catch (Exception e) {
            abortRecording("Couldn't start recording");
        }
    }

    private void abortRecording(String msg) {
        recording = false;
        MediaRecorder mr = recorder;
        Out o = recOut;
        recorder = null;
        recOut = null;
        if (mr != null) { try { mr.reset(); mr.release(); } catch (Exception ignored) { } }
        finishOutput(o, false);
        if (hud != null) { hud.setMessage(msg); hud.invalidate(); }
        startPreview();
    }

    private void stopRecording() {
        if (!recording) return;
        recording = false;
        final MediaRecorder mr = recorder;
        final Out o = recOut;
        recorder = null;
        recOut = null;
        boolean ok = true;
        try { if (session != null) { session.stopRepeating(); session.abortCaptures(); } } catch (Exception ignored) { }
        try { mr.stop(); } catch (RuntimeException e) { ok = false; } // dura menos de ~1 s
        try { mr.reset(); mr.release(); } catch (Exception ignored) { }
        play(MediaActionSound.STOP_VIDEO_RECORDING);
        finishOutput(o, ok);
        if (ok && o != null && Build.VERSION.SDK_INT >= 29) videoThumb(o.uri);
        if (hud != null) hud.invalidate();
        if (shown && device != null) startPreview();
    }

    private void videoThumb(final Uri uri) {
        startThread();
        bg.post(() -> {
            Bitmap b = null;
            MediaMetadataRetriever r = new MediaMetadataRetriever();
            try {
                r.setDataSource(ctx, uri);
                Bitmap f = r.getFrameAtTime(0);
                if (f != null) {
                    int s = Math.max(1, Math.max(f.getWidth(), f.getHeight()) / 240);
                    b = Bitmap.createScaledBitmap(f, f.getWidth() / s, f.getHeight() / s, true);
                }
            } catch (Exception ignored) {
            } finally {
                try { r.release(); } catch (Exception ignored) { }
            }
            final Bitmap fb = b;
            ui.post(() -> { if (hud != null && fb != null) hud.setThumb(fb); });
        });
    }

    // ------------------------------------------------------------------ acciones del usuario

    private void shutter() {
        if (device == null || session == null) return;
        if (video) {
            if (recording) stopRecording(); else startRecording();
        } else {
            takePhoto();
        }
    }

    private void setVideo(boolean v) {
        if (recording || v == video) return;
        video = v;
        if (video && !has(Manifest.permission.RECORD_AUDIO)) {
            // sin permiso se graba sin audio; se pregunta una vez
            host.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, () -> { });
        }
        if (device != null) {
            configureSizes();
            startPreview();
        }
        hud.invalidate();
    }

    private void cycleFlash() {
        if (!hasFlash) return;
        flash = video ? (flash == FLASH_ON ? FLASH_OFF : FLASH_ON) : (flash + 1) % 3;
        if (session != null && state == ST_PREVIEW) pushPreview();
        hud.invalidate();
    }

    private void flip() {
        if (recording || backId == null || frontId == null) return;
        front = !front;
        closeCamera();
        tryOpen();
    }

    private void setZoom(float z) {
        zoom = Math.max(1f, Math.min(maxZoom, z));
        if (session != null && state == ST_PREVIEW) pushPreview();
        hud.invalidate();
    }

    // ------------------------------------------------------------------ vista con la proporción de la cámara

    private final class FitView extends TextureView {
        private int bw, bh; // tamaño del búfer (apaisado)

        FitView(Context c) { super(c); }

        void setBuffer(int w, int h) {
            if (w == bw && h == bh) return;
            bw = w;
            bh = h;
            requestLayout();
        }

        private boolean portrait() {
            int r = displayRotation();
            return r == Surface.ROTATION_0 || r == Surface.ROTATION_180;
        }

        @Override protected void onMeasure(int wm, int hm) {
            int W = MeasureSpec.getSize(wm), H = MeasureSpec.getSize(hm);
            if (bw == 0 || bh == 0) { setMeasuredDimension(W, H); return; }
            float ratio = portrait() ? (float) bh / bw : (float) bw / bh; // ancho/alto en pantalla
            int w = W, h = Math.round(W / ratio);
            if (h > H) { h = H; w = Math.round(H * ratio); }
            setMeasuredDimension(w, h);
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            applyTransform();
        }

        void applyTransform() {
            int vw = getWidth(), vh = getHeight();
            if (vw == 0 || vh == 0 || previewSize == null) return;
            int rot = displayRotation();
            Matrix m = new Matrix();
            RectF view = new RectF(0, 0, vw, vh);
            float cx = view.centerX(), cy = view.centerY();
            if (rot == Surface.ROTATION_90 || rot == Surface.ROTATION_270) {
                RectF buf = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
                buf.offset(cx - buf.centerX(), cy - buf.centerY());
                m.setRectToRect(view, buf, Matrix.ScaleToFit.FILL);
                float s = Math.max((float) vh / previewSize.getHeight(), (float) vw / previewSize.getWidth());
                m.postScale(s, s, cx, cy);
                m.postRotate(90 * (rot - 2), cx, cy);
            } else if (rot == Surface.ROTATION_180) {
                m.postRotate(180, cx, cy);
            }
            if (front) m.postScale(-1f, 1f, cx, cy); // la cámara frontal se ve como un espejo
            setTransform(m);
        }
    }


    // ------------------------------------------------------------------ ajustes (general | photo | video)

    private void showSettings() {
        inSettings = true;
        closeCamera();
        final Context c = getContext();
        View[] pages = {settingsGeneral(c), settingsPhoto(c), settingsVideo(c)};
        com.woba.slauncher.ui.Pivot pv = new com.woba.slauncher.ui.Pivot(c, "settings", new String[]{"general", "photo", "video"}, pages);
        swapContent(pv, null);
    }

    private LinearLayout settingsCol(Context c) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Theme.dp(4), Theme.dp(8), Theme.dp(4), Theme.dp(24));
        return col;
    }

    private View scroll(Context c, LinearLayout col) {
        android.widget.ScrollView sv = new android.widget.ScrollView(c);
        sv.addView(col, new android.widget.ScrollView.LayoutParams(-1, -2));
        return sv;
    }

    private TextView small(Context c, String t) {
        TextView v = WpUi.label(c, t, 14, Theme.regular, Theme.subtle);
        v.setPadding(0, Theme.dp(14), 0, Theme.dp(4));
        return v;
    }

    /** Lista desplegable de WP: caja con borde blanco y el valor actual. */
    private TextView combo(final Context c, String value, final String[] options, final com.woba.slauncher.sim.SimApp.Choice cb) {
        final TextView v = WpUi.label(c, value, 18, Theme.regular, Theme.fg);
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setStroke(Theme.dp(2), Theme.fg);
        d.setColor(0);
        v.setBackground(d);
        v.setPadding(Theme.dp(10), Theme.dp(9), Theme.dp(10), Theme.dp(9));
        v.setOnClickListener(x -> {
            int[] loc = new int[2];
            v.getLocationOnScreen(loc);
            int[] me = new int[2];
            getLocationOnScreen(me);
            showMenu(getWidth() / 2f, loc[1] - me[1], options, cb);
        });
        return v;
    }

    private View settingsGeneral(final Context c) {
        LinearLayout col = settingsCol(c);
        col.addView(small(c, "Framing grids"));
        final String[] g = {"off", "rule of thirds", "golden ratio"};
        String cur = com.woba.slauncher.data.Prefs.getStr(ctx, "cam_grid", "off");
        col.addView(combo(c, cur, g, i -> { com.woba.slauncher.data.Prefs.setStr(ctx, "cam_grid", g[i]); showSettings(); }));
        TextView b = WpUi.button(c, "use default settings");
        b.setPadding(Theme.dp(12), 0, Theme.dp(12), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, Theme.dp(44));
        lp.topMargin = Theme.dp(26);
        b.setOnClickListener(x -> {
            com.woba.slauncher.data.Prefs.setStr(ctx, "cam_grid", "off");
            com.woba.slauncher.data.Prefs.setStr(ctx, "cam_ratio", "16:9");
            com.woba.slauncher.data.Prefs.setStr(ctx, "cam_vq", "1080p");
            com.woba.slauncher.data.Prefs.setStr(ctx, "cam_rich", "auto");
            wbIdx = -1; focusV = isoV = expV = -1f; evV = 0.5f;
            showSettings();
        });
        col.addView(b, lp);
        TextView n = WpUi.label(c, "Lumia Camera saves photos and videos in your gallery (DCIM/Camera). Location tagging follows your system settings.",
                14, Theme.regular, Theme.subtle);
        n.setPadding(0, Theme.dp(24), 0, 0);
        col.addView(n);
        return scroll(c, col);
    }

    private View settingsPhoto(final Context c) {
        LinearLayout col = settingsCol(c);
        col.addView(small(c, "Aspect ratio"));
        final String[] r = {"16:9", "4:3"};
        col.addView(combo(c, com.woba.slauncher.data.Prefs.getStr(ctx, "cam_ratio", "16:9"), r,
                i -> { com.woba.slauncher.data.Prefs.setStr(ctx, "cam_ratio", r[i]); showSettings(); }));
        col.addView(small(c, "Rich capture"));
        final String[] rc = {"auto", "hdr", "night", "off"};
        col.addView(combo(c, com.woba.slauncher.data.Prefs.getStr(ctx, "cam_rich", "auto"), rc,
                i -> { com.woba.slauncher.data.Prefs.setStr(ctx, "cam_rich", rc[i]); showSettings(); }));
        TextView rn = WpUi.label(c, "Takes several shots at once and combines them: hdr recovers highlights and shadows, "
                + "night averages shots to remove noise, auto picks one from the light. Saving takes a few seconds.",
                13, Theme.regular, Theme.subtle);
        rn.setPadding(0, Theme.dp(6), 0, 0);
        col.addView(rn);
        col.addView(small(c, "Capture mode"));
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setStroke(Theme.dp(2), Theme.accent);
        d.setColor(0);
        box.setBackground(d);
        TextView t = WpUi.label(c, "JPEG", 18, Theme.regular, Theme.accent);
        t.setPadding(Theme.dp(10), Theme.dp(9), Theme.dp(10), Theme.dp(9));
        box.addView(t);
        col.addView(box);
        return scroll(c, col);
    }

    private View settingsVideo(final Context c) {
        LinearLayout col = settingsCol(c);
        col.addView(small(c, "Video quality"));
        final String[] q = {"1080p", "720p"};
        col.addView(combo(c, com.woba.slauncher.data.Prefs.getStr(ctx, "cam_vq", "1080p"), q,
                i -> { com.woba.slauncher.data.Prefs.setStr(ctx, "cam_vq", q[i]); showSettings(); }));
        return scroll(c, col);
    }

    // ------------------------------------------------------------------ controles (calcados de Lumia Camera)

    /**
     * Geometría medida de una captura de Lumia Camera 5 en horizontal (406 px de alto = 1 unidad "u" de 1/406):
     * el obturador a la derecha (anillo r=33), a su izquierda el botón de exposición; los demás controles
     * (WB, enfoque, ISO, velocidad) están sobre anillos concéntricos centrados en el borde derecho, con su botón
     * abajo y su valor ("auto") a la izquierda. En vertical se gira todo 90° y los iconos quedan derechos.
     */
    private final class Hud extends View {
        private static final int B_SHUT = 1, B_MODE = 2, B_THUMB = 3, B_FLASH = 4, B_FLIP = 5, B_GEAR = 6, B_CTRL = 10;
        // radios de los anillos (x alto): exposición, velocidad, ISO, enfoque, balance de blancos
        private final float[] RK = {0.394f, 0.616f, 0.85f, 1.07f, 1.28f};

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final ScaleGestureDetector pinch;
        private Bitmap thumb;
        private String message;
        private long flashUntil;
        private boolean scaling, expanded;
        private int down, active = -1, dragRing = -1;

        private float W, H, AW, AH, u;
        private boolean port;

        Hud(Context c) {
            super(c);
            setClickable(true);
            pinch = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScaleBegin(ScaleGestureDetector d) { scaling = true; return true; }
                @Override public boolean onScale(ScaleGestureDetector d) { setZoom(zoom * d.getScaleFactor()); return true; }
                @Override public void onScaleEnd(ScaleGestureDetector d) { postDelayed(() -> scaling = false, 120); }
            });
        }

        void setThumb(Bitmap b) { thumb = b; invalidate(); }

        void setMessage(String m) { message = m; invalidate(); }

        private boolean busy;

        void setBusy(boolean b) { busy = b; invalidate(); }

        void flashAnim() { flashUntil = SystemClock.uptimeMillis() + 160; invalidate(); }

        boolean collapse() {
            if (!expanded) return false;
            expanded = false;
            active = -1;
            invalidate();
            return true;
        }

        // ---- geometría abstracta (horizontal) -> pantalla
        private void geo() {
            W = getWidth(); H = getHeight();
            port = H > W;
            AW = Math.max(W, H);
            AH = Math.min(W, H);
            u = AH / 406f;
        }
        private float X(float ax, float ay) { return port ? W - ay : ax; }
        private float Y(float ax, float ay) { return port ? ax : ay; }
        private float AX(float x, float y) { return port ? y : x; }
        private float AY(float x, float y) { return port ? W - x : y; }

        private float cx() { return AW - 15 * u; }
        private float cy() { return AH / 2f; }
        private float ringR(int i) { return AH * RK[i]; }
        private float dyTop() { return -0.4f * AH; }

        /** Punto del anillo i para el valor v (0 arriba, 1 abajo), en coordenadas abstractas. */
        private float ringX(int i, float v) {
            float dy = dyTop() + v * 0.8f * AH, R = ringR(i);
            return cx() - (float) Math.sqrt(Math.max(0, R * R - dy * dy));
        }
        private float ringY(float v) { return cy() + dyTop() + v * 0.8f * AH; }

        private boolean avail(int i) {
            switch (i) {
                case 0: return evRange != null && evRange.getUpper() > 0;
                case 1: return expRange != null && isoRange != null;
                case 2: return isoRange != null && expRange != null;
                case 3: return minFocus > 0;
                default: return awbModes != null;
            }
        }

        // centros de los botones (abstractos)
        private float bx(int id) {
            switch (id) {
                case B_SHUT: return AW - 95 * u;
                case B_MODE: return 37 * u;
                case B_THUMB: return 98 * u;
                case B_FLASH: return AW - 230 * u;
                case B_FLIP: return AW - 180 * u;
                case B_GEAR: return AW - 130 * u;
                default: {
                    int i = id - B_CTRL;
                    if (i == 0) return cx() - ringR(0);
                    float dy = 0.399f * AH, R = ringR(i);
                    return cx() - (float) Math.sqrt(R * R - dy * dy);
                }
            }
        }
        private float by(int id) {
            if (id == B_SHUT) return cy();
            if (id == B_MODE || id == B_THUMB || id == B_FLASH || id == B_FLIP || id == B_GEAR) return 31 * u;
            return id == B_CTRL ? cy() : AH * 0.9f;
        }
        private float br(int id) {
            if (id == B_SHUT) return 33 * u;
            if (id == B_MODE) return 22 * u;
            if (id == B_THUMB) return 21 * u;
            return 19 * u;
        }

        private boolean enabled(int id) {
            if (id == B_FLASH) return hasFlash;
            if (id == B_FLIP) return backId != null && frontId != null;
            if (id >= B_CTRL) return avail(id - B_CTRL);
            return true;
        }

        private int hit(float x, float y) {
            geo();
            float ax = AX(x, y), ay = AY(x, y);
            int[] ids = {B_SHUT, B_MODE, B_THUMB, B_FLASH, B_FLIP, B_GEAR, B_CTRL, B_CTRL + 1, B_CTRL + 2, B_CTRL + 3, B_CTRL + 4};
            for (int id : ids) {
                if (!enabled(id)) continue;
                float dx = ax - bx(id), dy = ay - by(id), rad = Math.max(br(id) + 8 * u, 26 * u);
                if (dx * dx + dy * dy <= rad * rad) return id;
            }
            return 0;
        }

        /** Anillo bajo el dedo (solo si están desplegados). */
        private int ringAt(float x, float y) {
            if (!expanded) return -1;
            geo();
            float ax = AX(x, y), ay = AY(x, y);
            if (Math.abs(ay - cy()) > 0.45f * AH) return -1;
            float d = (float) Math.hypot(ax - cx(), ay - cy());
            int best = -1;
            float bd = 0.05f * AH;
            for (int i = 0; i < 5; i++) {
                if (!avail(i)) continue;
                float e = Math.abs(d - ringR(i));
                if (e < bd) { bd = e; best = i; }
            }
            return best;
        }

        private void dragTo(int ring, float x, float y) {
            float v = Math.max(0f, Math.min(1f, (AY(x, y) - (cy() + dyTop())) / (0.8f * AH)));
            switch (ring) {
                case 0: evV = v; break;
                case 1: expV = v; break;
                case 2: isoV = v; break;
                case 3: focusV = v; break;
                default: wbIdx = Math.min(4, (int) (v * 5)); break;
            }
            active = ring;
            manualChanged();
        }

        private void resetCtl(int i) {
            switch (i) {
                case 0: evV = 0.5f; break;
                case 1: expV = -1f; break;
                case 2: isoV = -1f; break;
                case 3: focusV = -1f; break;
                default: wbIdx = -1; break;
            }
            manualChanged();
        }

        private boolean isAuto(int i) {
            switch (i) {
                case 0: return Math.abs(evV - 0.5f) < 0.01f;
                case 1: return expV < 0;
                case 2: return isoV < 0;
                case 3: return focusV < 0;
                default: return wbIdx < 0;
            }
        }

        // ---- toques
        @Override public boolean onTouchEvent(MotionEvent e) {
            pinch.onTouchEvent(e);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down = hit(e.getX(), e.getY());
                    dragRing = down == 0 ? ringAt(e.getX(), e.getY()) : -1;
                    if (dragRing >= 0) dragTo(dragRing, e.getX(), e.getY());
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (dragRing >= 0 && !scaling) dragTo(dragRing, e.getX(), e.getY());
                    return true;
                case MotionEvent.ACTION_UP: {
                    int d = down;
                    boolean dragged = dragRing >= 0;
                    down = 0;
                    dragRing = -1;
                    invalidate();
                    if (scaling || e.getPointerCount() > 1 || dragged) return true;
                    if (d == 0) { collapse(); return true; }
                    if (d != hit(e.getX(), e.getY())) return true;
                    click(d);
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    down = 0;
                    dragRing = -1;
                    invalidate();
                    return true;
                default:
                    return true;
            }
        }

        private void click(int d) {
            switch (d) {
                case B_SHUT: shutter(); break;
                case B_MODE: setVideo(!video); break;
                case B_THUMB: host.openSim("photos", null); break;
                case B_FLASH: cycleFlash(); break;
                case B_FLIP: flip(); break;
                case B_GEAR: if (!recording) showSettings(); break;
                default: {
                    int i = d - B_CTRL;
                    if (expanded && active == i) { resetCtl(i); expanded = false; active = -1; }
                    else { expanded = true; active = i; }
                    invalidate();
                }
            }
        }

        // ---- dibujo
        @Override protected void onDraw(Canvas c) {
            geo();
            drawGrid(c);
            if (expanded) drawDials(c);
            drawButtons(c);

            // cronómetro / zoom (arriba, centrados en pantalla)
            p.setTextAlign(Paint.Align.CENTER);
            p.setStyle(Paint.Style.FILL);
            if (recording) {
                long s = (SystemClock.elapsedRealtime() - recStart) / 1000;
                String t = String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
                p.setColor(0xFFE51400);
                c.drawCircle(W / 2f - 36 * u, 31 * u, 5 * u, p);
                p.setTypeface(Theme.light);
                p.setTextSize(20 * u);
                p.setColor(0xFFFFFFFF);
                c.drawText(t, W / 2f + 4 * u, Theme.centerBaseline(p, 31 * u), p);
                postInvalidateDelayed(250);
            }
            if (busy) {
                p.setTypeface(Theme.light);
                p.setTextSize(17 * u);
                p.setColor(0xFFFFFFFF);
                c.drawText("rich capture\u2026", W / 2f, 31 * u, p);
                postInvalidateDelayed(300);
            }
            if (zoom > 1.05f) {
                p.setTypeface(Theme.light);
                p.setTextSize(17 * u);
                p.setColor(0xFFFFFFFF);
                c.drawText(String.format(Locale.US, "%.1f×", zoom), W / 2f, 62 * u, p);
            }
            long left = flashUntil - SystemClock.uptimeMillis();
            if (left > 0) {
                p.setColor(0xFFFFFFFF);
                p.setAlpha((int) (140 * left / 160f));
                c.drawRect(0, 0, W, H, p);
                p.setAlpha(255);
                postInvalidateOnAnimation();
            }
            if (message != null) {
                p.setTypeface(Theme.light);
                p.setTextSize(20 * u);
                p.setColor(0xFFFFFFFF);
                c.drawText(message, W / 2f, H / 2f, p);
            }
            p.setTextAlign(Paint.Align.LEFT);
        }

        private void drawGrid(Canvas c) {
            String g = com.woba.slauncher.data.Prefs.getStr(ctx, "cam_grid", "off");
            if ("off".equals(g) || tv == null || tv.getWidth() == 0) return;
            float l = tv.getLeft(), t = tv.getTop(), rr = l + tv.getWidth(), b = t + tv.getHeight();
            float a = "golden ratio".equals(g) ? 0.382f : 1f / 3f, bb = "golden ratio".equals(g) ? 0.618f : 2f / 3f;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, u));
            p.setColor(0x99FFFFFF);
            for (float f : new float[]{a, bb}) {
                c.drawLine(l + (rr - l) * f, t, l + (rr - l) * f, b, p);
                c.drawLine(l, t + (b - t) * f, rr, t + (b - t) * f, p);
            }
        }

        private String valueText(int i) {
            switch (i) {
                case 0: { float ev = evRange == null ? 0 : evValue(); return Math.abs(ev) < 0.05f ? "0.0" : String.format(Locale.US, "%+.1f", ev); }
                case 1: return expV < 0 || expRange == null ? "auto" : expText(expValue(expV));
                case 2: return isoV < 0 || isoRange == null ? "auto" : String.valueOf(isoValue(isoV));
                case 3: return focusV < 0 ? "auto" : focusText(focusV);
                default: return wbIdx < 0 ? "auto ☀" : WB_NAMES[wbIdx];
            }
        }

        private void drawDials(Canvas c) {
            float ccx = X(cx(), cy()), ccy = Y(cx(), cy());
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x40000000);
            c.drawCircle(ccx, ccy, 1.5f * AH, p); // el velo oscuro detrás de los anillos
            for (int i = 0; i < 5; i++) {
                if (!avail(i)) continue;
                boolean act = active == i;
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth((act ? 2.2f : 1.3f) * Math.max(1f, u));
                p.setColor(act ? 0xFFFFFFFF : 0xE6FFFFFF);
                c.drawCircle(ccx, ccy, ringR(i), p);

                // puntos de escala
                p.setStyle(Paint.Style.FILL);
                for (int k = 0; k <= 10; k++) c.drawCircle(X(ringX(i, k / 10f), ringY(k / 10f)), Y(ringX(i, k / 10f), ringY(k / 10f)), 1.4f * u, p);

                // números sobre el anillo
                float[] vs;
                String[] tx;
                switch (i) {
                    case 0: vs = new float[]{0f, 0.2167f, 0.7833f, 1f}; tx = new String[]{"3.0", "1.7", "-1.7", "-3.0"}; break;
                    case 1: vs = new float[]{0.12f, 0.5f, 0.88f}; tx = new String[]{expText(expValue(0.12f)), expText(expValue(0.5f)), expText(expValue(0.88f))}; break;
                    case 2: vs = new float[]{0.12f, 0.5f, 0.88f}; tx = new String[]{"" + isoValue(0.12f), "" + isoValue(0.5f), "" + isoValue(0.88f)}; break;
                    case 3: vs = new float[]{0.12f, 0.5f, 0.88f}; tx = new String[]{focusText(0.12f), focusText(0.5f), focusText(0.88f)}; break;
                    default: vs = new float[]{0.1f, 0.3f, 0.5f, 0.7f, 0.9f}; tx = WB_NAMES; break;
                }
                p.setTypeface(Theme.regular);
                p.setTextSize(9.5f * u);
                p.setTextAlign(Paint.Align.CENTER);
                p.setColor(0xFFFFFFFF);
                for (int k = 0; k < vs.length; k++) {
                    float ax = ringX(i, vs[k]) - 9 * u, ay = ringY(vs[k]);
                    double alpha = Math.toDegrees(Math.asin((ay - cy()) / ringR(i)));
                    float rot = (float) (-90 - alpha) + (port ? 90 : 0);
                    int sc = c.save();
                    c.translate(X(ax, ay), Y(ax, ay));
                    c.rotate(rot);
                    c.drawText(tx[k], 0, Theme.centerBaseline(p, 0), p);
                    c.restoreToCount(sc);
                }

                // marca del valor manual
                if (!isAuto(i)) {
                    float v = i == 0 ? evV : i == 1 ? expV : i == 2 ? isoV : i == 3 ? focusV : (wbIdx + 0.5f) / 5f;
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(0xFFFFFFFF);
                    c.drawCircle(X(ringX(i, v), ringY(v)), Y(ringX(i, v), ringY(v)), 5 * u, p);
                }

                // valor a la izquierda del anillo ("auto", "0.0"...)
                float lx = cx() - ringR(i) - 42 * u, ly = cy();
                p.setStyle(Paint.Style.FILL);
                p.setTypeface(Theme.light);
                p.setTextSize(20 * u);
                p.setColor(act ? 0xFFFFFFFF : 0x99FFFFFF);
                c.drawText(valueText(i), X(lx, ly), Theme.centerBaseline(p, Y(lx, ly)), p);
            }
            p.setTextAlign(Paint.Align.LEFT);
        }

        private void circleBtn(Canvas c, int id, boolean filled, int fillColor) {
            float x = X(bx(id), by(id)), y = Y(bx(id), by(id)), rad = br(id);
            if (filled) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(fillColor);
                c.drawCircle(x, y, rad, p);
            } else {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(1.5f, 2f * u));
                p.setColor(0xFFFFFFFF);
                c.drawCircle(x, y, rad - u, p);
            }
        }

        private void drawButtons(Canvas c) {
            // obturador: anillo blanco con la cámara dentro
            {
                int id = B_SHUT;
                float x = X(bx(id), by(id)), y = Y(bx(id), by(id)), rad = br(id);
                p.setStyle(Paint.Style.FILL);
                if (down == id) { p.setColor(Theme.alpha(0xFFFFFFFF, 0.35f)); c.drawCircle(x, y, rad, p); }
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(2f, 3f * u));
                p.setColor(0xFFFFFFFF);
                c.drawCircle(x, y, rad - 1.5f * u, p);
                if (recording) {
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(0xFFE51400);
                    float q = rad * 0.38f;
                    r.set(x - q, y - q, x + q, y + q);
                    c.drawRoundRect(r, 3 * u, 3 * u, p);
                } else {
                    Glyph.draw(c, video ? Glyph.VIDEO : Glyph.CAMERA, x, y, rad * 1.05f, 0xFFFFFFFF);
                }
            }
            // arriba a la izquierda: modo (foto/vídeo) y miniatura redonda
            {
                circleBtn(c, B_MODE, false, 0);
                float x = X(bx(B_MODE), by(B_MODE)), y = Y(bx(B_MODE), by(B_MODE));
                Glyph.draw(c, video ? Glyph.VIDEO : Glyph.CAMERA, x, y, br(B_MODE) * 0.95f, 0xFFFFFFFF);

                float tx = X(bx(B_THUMB), by(B_THUMB)), ty = Y(bx(B_THUMB), by(B_THUMB)), tr = br(B_THUMB);
                if (thumb != null) {
                    int sc = c.save();
                    android.graphics.Path clip = new android.graphics.Path();
                    clip.addCircle(tx, ty, tr, android.graphics.Path.Direction.CW);
                    c.clipPath(clip);
                    float s = Math.max(2 * tr / thumb.getWidth(), 2 * tr / thumb.getHeight());
                    float dw = thumb.getWidth() * s, dh = thumb.getHeight() * s;
                    c.drawBitmap(thumb, null, new RectF(tx - dw / 2f, ty - dh / 2f, tx + dw / 2f, ty + dh / 2f), p);
                    c.restoreToCount(sc);
                } else {
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(0x66000000);
                    c.drawCircle(tx, ty, tr, p);
                    Glyph.draw(c, Glyph.PHOTOS, tx, ty, tr * 1.0f, 0xCCFFFFFF);
                }
            }
            // arriba a la derecha: flash, girar cámara, ajustes (círculos de contorno)
            if (hasFlash) {
                circleBtn(c, B_FLASH, false, 0);
                int col = flash == FLASH_OFF ? 0xFFFFFFFF : Theme.accent;
                Glyph.draw(c, Glyph.FLASH, X(bx(B_FLASH), by(B_FLASH)), Y(bx(B_FLASH), by(B_FLASH)), br(B_FLASH), col);
                p.setStyle(Paint.Style.FILL);
                p.setTypeface(Theme.regular);
                p.setTextSize(9 * u);
                p.setTextAlign(Paint.Align.CENTER);
                p.setColor(col);
                String t = video ? (flash == FLASH_ON ? "on" : "off") : (flash == FLASH_AUTO ? "auto" : flash == FLASH_ON ? "on" : "off");
                float lx = bx(B_FLASH), ly = by(B_FLASH) + br(B_FLASH) + 10 * u;
                c.drawText(t, X(lx, ly), Theme.centerBaseline(p, Y(lx, ly)), p);
                p.setTextAlign(Paint.Align.LEFT);
            }
            if (enabled(B_FLIP)) {
                circleBtn(c, B_FLIP, false, 0);
                Glyph.draw(c, Glyph.ROTATE, X(bx(B_FLIP), by(B_FLIP)), Y(bx(B_FLIP), by(B_FLIP)), br(B_FLIP), 0xFFFFFFFF);
            }
            circleBtn(c, B_GEAR, false, 0);
            Glyph.draw(c, Glyph.GEAR, X(bx(B_GEAR), by(B_GEAR)), Y(bx(B_GEAR), by(B_GEAR)), br(B_GEAR), 0xFFFFFFFF);

            // controles manuales: botones blancos con el icono en negro; en acento si están en manual
            for (int i = 0; i < 5; i++) {
                int id = B_CTRL + i;
                if (!avail(i)) continue;
                boolean man = !isAuto(i);
                int fill = man ? Theme.accent : 0xFFFFFFFF, ink = man ? 0xFFFFFFFF : 0xFF000000;
                if (down == id) fill = 0xFFB0B0B0;
                circleBtn(c, id, true, fill);
                float x = X(bx(id), by(id)), y = Y(bx(id), by(id)), rad = br(id);
                if (active == i && expanded) {
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(Math.max(1.5f, 2f * u));
                    p.setColor(0xFFFFFFFF);
                    c.drawCircle(x, y, rad + 4 * u, p);
                }
                p.setColor(ink);
                p.setTextAlign(Paint.Align.CENTER);
                switch (i) {
                    case 0: { // +/-
                        p.setStyle(Paint.Style.STROKE);
                        p.setStrokeWidth(Math.max(1.5f, 1.8f * u));
                        p.setStrokeCap(Paint.Cap.ROUND);
                        float q = rad * 0.5f;
                        c.drawLine(x - q, y + q, x + q, y - q, p);
                        c.drawLine(x - q, y - q * 0.55f, x - q * 0.3f, y - q * 0.55f, p);
                        c.drawLine(x - q * 0.65f, y - q * 0.9f, x - q * 0.65f, y - q * 0.2f, p);
                        c.drawLine(x + q * 0.3f, y + q * 0.55f, x + q, y + q * 0.55f, p);
                        break;
                    }
                    case 1: { // velocidad: anillo con muesca
                        p.setStyle(Paint.Style.STROKE);
                        p.setStrokeWidth(Math.max(1.5f, 2.4f * u));
                        r.set(x - rad * 0.5f, y - rad * 0.5f, x + rad * 0.5f, y + rad * 0.5f);
                        c.drawArc(r, -60, 300, false, p);
                        break;
                    }
                    case 2:
                        p.setStyle(Paint.Style.FILL);
                        p.setTypeface(Theme.bold);
                        p.setTextSize(rad * 0.72f);
                        c.drawText("ISO", x, Theme.centerBaseline(p, y), p);
                        break;
                    case 3: { // enfoque: cuadrado
                        p.setStyle(Paint.Style.STROKE);
                        p.setStrokeWidth(Math.max(1.5f, 2.2f * u));
                        float q = rad * 0.28f;
                        c.drawRect(x - q, y - q, x + q, y + q, p);
                        break;
                    }
                    default:
                        p.setStyle(Paint.Style.FILL);
                        p.setTypeface(Theme.bold);
                        p.setTextSize(rad * 0.82f);
                        c.drawText("WB", x, Theme.centerBaseline(p, y), p);
                        break;
                }
                p.setTextAlign(Paint.Align.LEFT);
            }
        }
    }
}
