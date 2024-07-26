/*
 * *
 *  * Created by Théo Mougnibas on 05/07/2024 21:16
 *  * Copyright (c) 2024 . All rights reserved.
 *  * Last modified 05/07/2024 21:16
 *
 */

package com.ecosys.ecosys;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;

public class FolderPickerReceiver extends ResultReceiver {

    private FolderPickerCallback callback;

    public FolderPickerReceiver(Handler handler, FolderPickerCallback callback) {
        super(handler);
        this.callback = callback;
    }

    @Override
    protected void onReceiveResult(int resultCode, Bundle resultData) {
        if (resultCode == Activity.RESULT_OK) {
            Uri uri = resultData.getParcelable("folder_uri");
            //Log.d("FolderPickerReceiver",uri.getPath());
            if (callback != null) {
                callback.onFolderPicked(uri);
            }
        }
    }
}

