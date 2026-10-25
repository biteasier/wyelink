/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Same descriptor and transaction ids as androidx.browser's ICustomTabsService.aidl.
// Callback interfaces are declared as IBinder: identical on the wire (writeStrongBinder),
// and Wyelink never calls back, so their definitions aren't needed.
package android.support.customtabs;

import android.net.Uri;
import android.os.Bundle;

interface ICustomTabsService {
    boolean warmup(long flags) = 1;
    boolean newSession(in IBinder callback) = 2;
    boolean newSessionWithExtras(in IBinder callback, in Bundle extras) = 9;
    boolean mayLaunchUrl(in IBinder callback, in Uri url, in Bundle extras, in List<Bundle> otherLikelyBundles) = 3;
    void prefetch(in IBinder callback, in Uri url, in Bundle options) = 14;
    void prefetchWithMultipleUrls(in IBinder callback, in List<Uri> urls, in Bundle options) = 15;
    Bundle extraCommand(String commandName, in Bundle args) = 4;
    boolean updateVisuals(in IBinder callback, in Bundle bundle) = 5;
    boolean requestPostMessageChannel(in IBinder callback, in Uri postMessageOrigin) = 6;
    boolean requestPostMessageChannelWithExtras(in IBinder callback, in Uri postMessageOrigin, in Bundle extras) = 10;
    int postMessage(in IBinder callback, String message, in Bundle extras) = 7;
    boolean validateRelationship(in IBinder callback, int relation, in Uri origin, in Bundle extras) = 8;
    boolean receiveFile(in IBinder callback, in Uri uri, int purpose, in Bundle extras) = 11;
    boolean isEngagementSignalsApiAvailable(in IBinder customTabsCallback, in Bundle extras) = 12;
    boolean setEngagementSignalsCallback(in IBinder customTabsCallback, in IBinder callback, in Bundle extras) = 13;
    boolean newAuthTabSession(in IBinder callback, in Bundle extras) = 17;
}
