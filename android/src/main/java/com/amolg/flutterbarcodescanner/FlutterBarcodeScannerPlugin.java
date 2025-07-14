package com.amolg.flutterbarcodescanner;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.vision.barcode.Barcode;

import java.util.Map;
import java.util.HashMap;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.EventChannel.StreamHandler;
import io.flutter.embedding.engine.plugins.lifecycle.FlutterLifecycleAdapter;

/**
 * FlutterBarcodeScannerPlugin
 */
public class FlutterBarcodeScannerPlugin implements MethodCallHandler, FlutterPlugin, ActivityAware, StreamHandler, io.flutter.plugin.common.PluginRegistry.ActivityResultListener {
    private static final String CHANNEL = "flutter_barcode_scanner";
    private static final String EVENT_CHANNEL = "flutter_barcode_scanner_receiver";
    private static final int RC_BARCODE_CAPTURE = 9001;
    private static final String TAG = FlutterBarcodeScannerPlugin.class.getSimpleName();

    private static Activity activity;
    private static Result pendingResult;
    private static EventChannel.EventSink barcodeStream;

    private MethodChannel channel;
    private EventChannel eventChannel;
    private FlutterPluginBinding pluginBinding;
    private ActivityPluginBinding activityBinding;
    private Application applicationContext;
    private Lifecycle lifecycle;
    private LifeCycleObserver observer;

    private Map<String, Object> arguments;
    public static String lineColor = "";
    public static boolean isShowFlashIcon = false;
    public static boolean isContinuousScan = false;

    public FlutterBarcodeScannerPlugin() {}

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        pluginBinding = binding;
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        pluginBinding = null;
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        activityBinding = binding;
        createPluginSetup(
            pluginBinding.getBinaryMessenger(),
            (Application) pluginBinding.getApplicationContext(),
            binding.getActivity(),
            binding
        );
    }

    @Override
    public void onDetachedFromActivity() {
        clearPluginSetup();
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        onAttachedToActivity(binding);
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity();
    }

    private void createPluginSetup(BinaryMessenger messenger, Application appContext, Activity act, ActivityPluginBinding binding) {
        activity = act;
        applicationContext = appContext;

        channel = new MethodChannel(messenger, CHANNEL);
        channel.setMethodCallHandler(this);

        eventChannel = new EventChannel(messenger, EVENT_CHANNEL);
        eventChannel.setStreamHandler(this);

        binding.addActivityResultListener(this);
        lifecycle = FlutterLifecycleAdapter.getActivityLifecycle(binding);
        observer = new LifeCycleObserver(act);
        lifecycle.addObserver(observer);
        appContext.registerActivityLifecycleCallbacks(observer);
    }

    private void clearPluginSetup() {
        if (activityBinding != null) {
            activityBinding.removeActivityResultListener(this);
            activityBinding = null;
        }
        if (lifecycle != null && observer != null) {
            lifecycle.removeObserver(observer);
        }
        if (applicationContext != null && observer != null) {
            applicationContext.unregisterActivityLifecycleCallbacks(observer);
        }
        channel.setMethodCallHandler(null);
        eventChannel.setStreamHandler(null);
        activity = null;
        applicationContext = null;
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        pendingResult = result;
        if (call.method.equals("scanBarcode")) {
            arguments = (Map<String, Object>) call.arguments;
            lineColor = (String) arguments.get("lineColor");
            isShowFlashIcon = (boolean) arguments.get("isShowFlashIcon");
            isContinuousScan = (boolean) arguments.get("isContinuousScan");

            if (lineColor == null || lineColor.isEmpty()) {
                lineColor = "#DC143C";
            }

            Object scanModeObj = arguments.get("scanMode");
            BarcodeCaptureActivity.SCAN_MODE = scanModeObj instanceof Integer ? (Integer) scanModeObj : BarcodeCaptureActivity.SCAN_MODE_ENUM.QR.ordinal();

            String cancelButtonText = (String) arguments.get("cancelButtonText");
            startBarcodeScannerActivityView(cancelButtonText, isContinuousScan);
        } else {
            result.notImplemented();
        }
    }

    private void startBarcodeScannerActivityView(String buttonText, boolean continuousScan) {
        try {
            Intent intent = new Intent(activity, BarcodeCaptureActivity.class)
                    .putExtra("cancelButtonText", buttonText);
            if (continuousScan) {
                activity.startActivity(intent);
            } else {
                activity.startActivityForResult(intent, RC_BARCODE_CAPTURE);
            }
        } catch (Exception e) {
            Log.e(TAG, "startBarcodeScannerActivityView: ", e);
        }
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_BARCODE_CAPTURE) {
            if (resultCode == CommonStatusCodes.SUCCESS && data != null) {
                try {
                    Barcode barcode = data.getParcelableExtra(BarcodeCaptureActivity.BarcodeObject);
                    BarcodeTypes formats = new BarcodeTypes();
                    HashMap<String, String> result = new HashMap<>();
                    result.put("data", barcode.rawValue);
                    result.put("format", formats.getFormat(barcode.format));
                    pendingResult.success(result);
                } catch (Exception e) {
                    pendingResult.error("error", e.toString(), null);
                }
            } else {
                pendingResult.error("error", "Scan cancelled or failed", null);
            }
            pendingResult = null;
            arguments = null;
            return true;
        }
        return false;
    }

    @Override
    public void onListen(Object arguments, EventChannel.EventSink events) {
        barcodeStream = events;
    }

    @Override
    public void onCancel(Object arguments) {
        barcodeStream = null;
    }

    public static void onBarcodeScanReceiver(final Barcode barcode) {
        if (barcode != null && barcodeStream != null && !barcode.displayValue.isEmpty()) {
            activity.runOnUiThread(() -> barcodeStream.success(barcode.rawValue));
        }
    }

    private static class LifeCycleObserver implements Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {
        private final Activity activity;

        LifeCycleObserver(Activity activity) {
            this.activity = activity;
        }

        @Override public void onCreate(@NonNull LifecycleOwner owner) {}
        @Override public void onStart(@NonNull LifecycleOwner owner) {}
        @Override public void onResume(@NonNull LifecycleOwner owner) {}
        @Override public void onPause(@NonNull LifecycleOwner owner) {}
        @Override public void onStop(@NonNull LifecycleOwner owner) { onActivityStopped(activity); }
        @Override public void onDestroy(@NonNull LifecycleOwner owner) { onActivityDestroyed(activity); }

        @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
        @Override public void onActivityStarted(Activity activity) {}
        @Override public void onActivityResumed(Activity activity) {}
        @Override public void onActivityPaused(Activity activity) {}
        @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
        @Override public void onActivityStopped(Activity activity) {}

        @Override
        public void onActivityDestroyed(Activity activity) {
            if (this.activity == activity && activity.getApplicationContext() != null) {
                ((Application) activity.getApplicationContext()).unregisterActivityLifecycleCallbacks(this);
            }
        }
    }
}
