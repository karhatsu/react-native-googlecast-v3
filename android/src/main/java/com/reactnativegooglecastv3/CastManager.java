package com.reactnativegooglecastv3;

import android.content.Context;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.CastStateListener;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import java.io.IOException;

import static com.google.android.gms.cast.framework.CastState.CONNECTED;
import static com.google.android.gms.cast.framework.CastState.CONNECTING;
import static com.reactnativegooglecastv3.GoogleCastPackage.NAMESPACE;
import static com.reactnativegooglecastv3.GoogleCastPackage.TAG;
import static com.reactnativegooglecastv3.GoogleCastPackage.metadata;

public class CastManager {
    static CastManager instance;

    final Context parent;
    final CastContext castContext;
    final SessionManager sessionManager;
    final CastStateListenerImpl castStateListener;
    ReactContext reactContext;
    CastDevice castDevice;

    CastManager(Context parent) {
        this.parent = parent;
        CastContext castContext = null;
        SessionManager sessionManager = null;
        CastStateListenerImpl castStateListener = null;
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(parent) == ConnectionResult.SUCCESS) {
            try {
                castContext = CastContext.getSharedInstance(parent); // possible RuntimeException from this
                sessionManager = castContext.getSessionManager();
                castStateListener = new CastStateListenerImpl();
                castContext.addCastStateListener(castStateListener);
                sessionManager.addSessionManagerListener(new SessionManagerListenerImpl(), CastSession.class);
            } catch (RuntimeException re) {
                Log.w(TAG, "RuntimeException in CastManager.<init>. Cannot cast.", re);
            }
        } else {
            Log.w(TAG, "Google Play services not installed on device. Cannot cast.");
        }
        this.castContext = castContext;
        this.sessionManager = sessionManager;
        this.castStateListener = castStateListener;
    }

    public static void init(Context ctx) {
        instance = new CastManager(ctx);
    }

    public void sendMessage(String namespace, String message) {
        CastSession session = sessionManager.getCurrentCastSession();
        if (session == null) return;
        try {
            session.sendMessage(namespace, message);
        } catch (RuntimeException re) {
            Log.w(TAG, "RuntimeException in CastManager.sendMessage.", re);
        }
    }

    public void triggerStateChange() {
        this.castStateListener.onCastStateChanged(castContext.getCastState());
    }

    private class CastStateListenerImpl implements CastStateListener {
        @Override
        public void onCastStateChanged(int state) {
            Log.d(TAG, "onCastStateChanged: " + state);
            if (state == CONNECTING || state == CONNECTED) {
                castDevice = sessionManager.getCurrentCastSession().getCastDevice();
            } else {
                castDevice = null;
            }
            if (reactContext != null) reactContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit("googleCastStateChanged", state);
        }
    }

    private class SessionManagerListenerImpl extends SessionManagerListenerBase {
        @Override
        public void onSessionStarted(CastSession session, String sessionId) {
            setMessageReceivedCallbacks(session);
        }

        @Override
        public void onSessionResumed(CastSession session, boolean wasSuspended) {
            setMessageReceivedCallbacks(session);
        }

        private void setMessageReceivedCallbacks(CastSession session) {
            try {
                if (reactContext != null)
                    session.setMessageReceivedCallbacks(
                        metadata(NAMESPACE, "", reactContext),
                        new CastMessageReceivedCallback());
            } catch (IOException e) {
                Log.e(TAG, "Cast channel creation failed: ", e);
            }
        }
    }

    private class CastMessageReceivedCallback implements Cast.MessageReceivedCallback {
        @Override
        public void onMessageReceived(CastDevice castDevice, String namespace, String message) {
            Log.d(TAG, "onMessageReceived: " + namespace + " / " + message);
            if (reactContext == null) return;
            WritableMap map = Arguments.createMap();
            map.putString("namespace", namespace);
            map.putString("message", message);
            reactContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit("googleCastMessage", map);

        }
    }

}
