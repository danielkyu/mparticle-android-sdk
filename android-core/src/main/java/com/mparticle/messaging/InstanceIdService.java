package com.mparticle.messaging;

import com.google.android.gms.iid.InstanceIDListenerService;
import com.mparticle.MParticle;
import com.mparticle.internal.ConfigManager;
import com.mparticle.internal.PushRegistrationHelper;

/**
 * mParticle implementation of InstanceIDListenerService. In order to support push notifications, you must
 * include this Service within your app's AndroidManifest.xml with an intent-filter for 'com.google.android.gms.iid.InstanceID'
 */
public class InstanceIdService extends InstanceIDListenerService {

    @Override
    public void onTokenRefresh() {
        super.onTokenRefresh();
        try {
            PushRegistrationHelper.requestInstanceId(getApplicationContext());
        }catch (Exception e) {
            ConfigManager.log(MParticle.LogLevel.ERROR, "Error refreshing Instance ID: " + e.getMessage());
        }
    }
}