package com.gantasmo.swaycommand;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.*;
import android.webkit.*;
import android.view.WindowManager;
import android.widget.Toast;
import android.widget.FrameLayout;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class MainActivity extends Activity implements MidiHost.Listener {
    private static final String ORIGIN = "https://appassets.androidplatform.net";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayList<JSONArray> messages = new ArrayList<>();
    private WebView web;
    private MidiHost midi;
    private boolean ready, resumed;
    private volatile boolean accepting;
    private ValueCallback<Uri[]> fileCallback;
    private PermissionRequest microphoneRequest;
    private String exportText;
    private AudioManager audio;
    private final AudioManager.OnAudioFocusChangeListener focus = change -> {
        if (change < 0) runOnUiThread(() -> js("window.swayAndroid?.pause()"));
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        web = new WebView(this);
        web.setBackgroundColor(0xff05060a);
        FrameLayout container = new FrameLayout(this);
        container.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(container);
        // Keep controls clear of cutouts and gesture navigation on edge-to-edge Android.
        container.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true); // Android document picker URIs.
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.addJavascriptInterface(new Bridge(), "SwayNative");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !local(request.getUrl());
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (!local(uri)) return response(403, "Forbidden", "text/plain", new byte[0]);
                String path = uri.getPath();
                if (path == null || !path.startsWith("/assets/www/") || path.contains(".."))
                    return response(404, "Not Found", "text/plain", new byte[0]);
                try {
                    String file = path.substring("/assets/".length());
                    return new WebResourceResponse(mime(file), "UTF-8", getAssets().open(file));
                } catch (IOException e) { return response(404, "Not Found", "text/plain", new byte[0]); }
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                ready = false; accepting = false;
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (!local(Uri.parse(url))) return;
                ready = true; accepting = resumed;
                midi.publish();
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage msg) {
                android.util.Log.d("SwayCommand", msg.message() + " @" + msg.lineNumber()); return true;
            }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                startActivityForResult(intent, 10);
                return true;
            }
            @Override public void onPermissionRequest(PermissionRequest request) {
                if (!local(request.getOrigin()) || !Arrays.asList(request.getResources()).contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                    request.deny(); return;
                }
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                    request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                else {
                    if (microphoneRequest != null) microphoneRequest.deny();
                    microphoneRequest = request;
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 11);
                }
            }
            @Override public void onPermissionRequestCanceled(PermissionRequest request) {
                if (microphoneRequest == request) microphoneRequest = null;
            }
        });
        midi = new MidiHost(this, this);
        web.loadUrl(ORIGIN + "/assets/www/index.html");
    }
    private static boolean local(Uri uri) { return "https".equals(uri.getScheme()) && "appassets.androidplatform.net".equals(uri.getHost()) && uri.getPort() == -1; }
    private static String mime(String path) {
        if (path.endsWith(".js")) return "application/javascript";
        if (path.endsWith(".css")) return "text/css";
        if (path.endsWith(".html")) return "text/html";
        if (path.endsWith(".json") || path.endsWith(".sway")) return "application/json";
        String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(path));
        return type == null ? "application/octet-stream" : type;
    }
    private static WebResourceResponse response(int status, String reason, String mime, byte[] bytes) {
        return new WebResourceResponse(mime, "UTF-8", status, reason, Collections.emptyMap(), new ByteArrayInputStream(bytes));
    }
    private void js(String script) { if (ready) web.evaluateJavascript(script, null); }
    @Override public void ports(List<String[]> ports) {
        JSONArray list = new JSONArray();
        for (String[] port : ports) list.put(new JSONArray(Arrays.asList(port)));
        js("window.swayAndroid?.ports(" + list + ")");
        android.hardware.usb.UsbManager usb = (android.hardware.usb.UsbManager) getSystemService(USB_SERVICE);
        String detail = "No USB device detected. Check the data cable and hub.";
        if (usb != null && !usb.getDeviceList().isEmpty()) {
            ArrayList<String> labels = new ArrayList<>();
            for (android.hardware.usb.UsbDevice device : usb.getDeviceList().values()) {
                String name = device.getProductName();
                labels.add(name == null ? "USB " + device.getVendorId() + ":" + device.getProductId() : name);
            }
            detail = "USB seen: " + String.join(", ", labels) + "; no open MIDI input";
        }
        js("window.swayAndroid?.usbStatus(" + JSONObject.quote(detail) + ")");
    }
    @Override public void message(String id, int[] bytes, long timestamp) {
        if (!accepting) return;
        synchronized (messages) {
            if (messages.size() >= 2048) {
                messages.clear(); main.post(this::disconnected); return;
            }
            JSONArray data = new JSONArray();
            for (int b : bytes) data.put(b);
            messages.add(new JSONArray().put(id).put(data).put(timestamp / 1000000L));
            if (messages.size() == 1) main.postDelayed(this::flushMidi, 4);
        }
    }
    private void flushMidi() {
        JSONArray batch;
        synchronized (messages) { batch = new JSONArray(messages); messages.clear(); }
        if (accepting) js("window.swayAndroid?.midi(" + batch + ")");
    }
    @Override public void disconnected() {
        synchronized (messages) { messages.clear(); }
        js("window.swayAndroid?.panic()");
    }
    @Override public void error(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    @Override public void status(String message) { js("window.swayAndroid?.usbStatus(" + JSONObject.quote(message) + ")"); }
    public final class Bridge {
        @JavascriptInterface public void recheckMidi() {
            main.post(() -> {
                if (resumed) {
                    midi.stop(); midi.start();
                    main.postDelayed(() -> { if (resumed) midi.requestUsbAccess(); }, 1800);
                }
            });
        }
        @JavascriptInterface public void exportProject(String name, String text) {
            if (text == null || text.length() > 16 * 1024 * 1024) return;
            main.post(() -> {
                if (exportText != null) { error("Finish the current export first."); return; }
                exportText = text;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE);
                intent.putExtra(Intent.EXTRA_TITLE, name.replaceAll("[^a-zA-Z0-9._ -]", "_"));
                startActivityForResult(intent, 12);
            });
        }
        @JavascriptInterface public void requestAudioFocus() {
            main.post(() -> audio.requestAudioFocus(focus, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN));
        }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 10 && fileCallback != null) {
            ArrayList<Uri> uris = new ArrayList<>();
            if (result == RESULT_OK && data != null) {
                if (data.getClipData() != null) for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
                else if (data.getData() != null) uris.add(data.getData());
            }
            fileCallback.onReceiveValue(uris.isEmpty() ? null : uris.toArray(new Uri[0])); fileCallback = null;
        } else if (request == 12) {
            String text = exportText; exportText = null;
            if (result == RESULT_OK && data != null && data.getData() != null && text != null) {
                Uri target = data.getData();
                new Thread(() -> {
                    try (OutputStream out = getContentResolver().openOutputStream(target, "wt")) {
                        if (out == null) throw new IOException("No output stream");
                        out.write(text.getBytes(StandardCharsets.UTF_8));
                        main.post(() -> error("Project exported."));
                    } catch (IOException e) { main.post(() -> error("Export failed: " + e.getMessage())); }
                }, "project-export").start();
            }
        }
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 11 && microphoneRequest != null) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED)
                microphoneRequest.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            else microphoneRequest.deny();
            microphoneRequest = null;
        }
    }
    @Override protected void onResume() {
        super.onResume(); resumed = true; accepting = ready; web.onResume(); midi.start(); enterFullScreen();
    }
    private void enterFullScreen() {
        if (Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(android.view.WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                    | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused) enterFullScreen();
    }
    @Override protected void onPause() {
        accepting = false; resumed = false; midi.stop(); js("window.swayAndroid?.pause()");
        audio.abandonAudioFocus(focus); web.onPause(); super.onPause();
    }
    @Override protected void onDestroy() {
        midi.stop(); ready = false; main.removeCallbacksAndMessages(null);
        if (fileCallback != null) fileCallback.onReceiveValue(null);
        if (microphoneRequest != null) microphoneRequest.deny();
        web.removeJavascriptInterface("SwayNative"); web.destroy(); super.onDestroy();
    }
}
