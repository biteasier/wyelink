package app.wyelink;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.customtabs.ICustomTabsService;

import java.util.List;

/**
 * Makes Wyelink look like a Custom Tabs browser. Sign-in libraries such as AppAuth pick the first
 * browser that offers this service, so without it they skip Wyelink and open a sign-in page in
 * whichever tab-capable browser they find. Every call is accepted and ignored; the tab itself
 * arrives at {@link RouterActivity} and is forwarded to the chosen browser.
 */
public class CustomTabsPassThroughService extends Service {

    private static final int POST_MESSAGE_DISALLOWED = -1;

    private final ICustomTabsService.Stub binder = new ICustomTabsService.Stub() {
        @Override
        public boolean warmup(long flags) {
            return true;
        }

        @Override
        public boolean newSession(IBinder callback) {
            return true;
        }

        @Override
        public boolean newSessionWithExtras(IBinder callback, Bundle extras) {
            return true;
        }

        @Override
        public boolean mayLaunchUrl(IBinder callback, Uri url, Bundle extras, List<Bundle> otherLikelyBundles) {
            return true;
        }

        @Override
        public void prefetch(IBinder callback, Uri url, Bundle options) {
        }

        @Override
        public void prefetchWithMultipleUrls(IBinder callback, List<Uri> urls, Bundle options) {
        }

        @Override
        public Bundle extraCommand(String commandName, Bundle args) {
            return null;
        }

        @Override
        public boolean updateVisuals(IBinder callback, Bundle bundle) {
            return false;
        }

        @Override
        public boolean requestPostMessageChannel(IBinder callback, Uri postMessageOrigin) {
            return false;
        }

        @Override
        public boolean requestPostMessageChannelWithExtras(IBinder callback, Uri postMessageOrigin, Bundle extras) {
            return false;
        }

        @Override
        public int postMessage(IBinder callback, String message, Bundle extras) {
            return POST_MESSAGE_DISALLOWED;
        }

        @Override
        public boolean validateRelationship(IBinder callback, int relation, Uri origin, Bundle extras) {
            return false;
        }

        @Override
        public boolean receiveFile(IBinder callback, Uri uri, int purpose, Bundle extras) {
            return false;
        }

        @Override
        public boolean isEngagementSignalsApiAvailable(IBinder customTabsCallback, Bundle extras) {
            return false;
        }

        @Override
        public boolean setEngagementSignalsCallback(IBinder customTabsCallback, IBinder callback, Bundle extras) {
            return false;
        }

        @Override
        public boolean newAuthTabSession(IBinder callback, Bundle extras) {
            // Not offered: an Auth Tab needs the target browser to return the result itself.
            return false;
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
