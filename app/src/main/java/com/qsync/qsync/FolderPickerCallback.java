/*
 * *
 *  * Created by Théo Mougnibas on 05/07/2024 19:22
 *  * Copyright (c) 2024 . All rights reserved.
 *  * Last modified 05/07/2024 19:22
 *
 */

package com.qsync.qsync;

import android.net.Uri;

public interface FolderPickerCallback {
    void onFolderPicked(Uri uri);
}
