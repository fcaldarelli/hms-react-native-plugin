/*
    Copyright 2020-2022. Huawei Technologies Co., Ltd. All rights reserved.

    Licensed under the Apache License, Version 2.0 (the "License")
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package com.huawei.hms.rn.push.receiver;

import android.app.Application;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.huawei.hms.rn.push.constants.NotificationConstants;
import com.huawei.hms.rn.push.local.HmsLocalNotificationController;
import com.huawei.hms.rn.push.utils.BundleUtils;

public class HmsLocalNotificationActionsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, Intent intent) {

        String intentActionPrefix = context.getPackageName() + ".ACTION_";

        if (intent.getAction() == null || !intent.getAction().startsWith(intentActionPrefix))
            return;


        final Bundle bundle = intent.getBundleExtra(NotificationConstants.NOTIFICATION);
        if (bundle == null) return;

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return;

        int id = Integer.parseInt(BundleUtils.get(bundle, NotificationConstants.ID));


        if (BundleUtils.getB(bundle, NotificationConstants.AUTO_CANCEL, true)) {
            if (BundleUtils.contains(bundle, NotificationConstants.TAG)) {
                String tag = BundleUtils.get(bundle, NotificationConstants.TAG);
                notificationManager.cancel(tag, id);
            } else {
                notificationManager.cancel(id);
            }
        }

        if (BundleUtils.getB(bundle, NotificationConstants.INVOKE_APP, true)) {
            HmsLocalNotificationController hmsLocalNotificationController = new HmsLocalNotificationController((Application) context.getApplicationContext());

            hmsLocalNotificationController.invokeApp(bundle);
        } else {

            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(new NotificationActionHandler(context, bundle));
        }
    }

}