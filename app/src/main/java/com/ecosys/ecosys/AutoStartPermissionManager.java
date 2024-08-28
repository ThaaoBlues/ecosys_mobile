/*
 * *
 *  * Created by Théo Mougnibas on 28/08/2024 19:10
 *  * Copyright (c) 2024 . All rights reserved.
 *  * Last modified 28/08/2024 19:10
 *
 */

package com.ecosys.ecosys;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

public class AutoStartPermissionManager {

    private static final String TAG = "AutoStartPermissionManager";

    private final Context _context;

    public AutoStartPermissionManager( Context context) {
        this._context = context;
    }

    /**
     * Request AutoStart permission based on [Brand] type.
     * Note -> No permission required for [Brand.OTHER].
     */
    public void requestAutoStartPermission() {

        Log.d(TAG,"BRAND :"+getBrand());
        switch (getBrand()) {
            case XIAOMI:
            case REDMI:
                xiaomiAutoStart();
                break;
            case NOKIA:
                nokiaAutoStart();
                break;
            case LETV:
                letvAutoStart();
                break;
            case ASUS:
                asusAutoStart();
                break;
            case HONOR:
                honorAutoStart();
                break;
            case OPPO:
                oppoAutoStart();
                break;
            case VIVO:
                vivoAutoStart();
                break;
            case OTHER:
                // No action needed for OTHER
                break;
        }
    }

    private void xiaomiAutoStart() {
        if (isPackageExists(BrandPackage.XIAOMI_MAIN)) {
            try {
                startAutoStartActivity(BrandPackage.XIAOMI_MAIN, BrandPackage.XIAOMI_COMPONENT);
            } catch (BAExceptions.ActivityNotFound e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }
    }

    private void nokiaAutoStart() {
        if (isPackageExists(BrandPackage.NOKIA_MAIN)) {
            try {
                startAutoStartActivity(BrandPackage.NOKIA_MAIN, BrandPackage.NOKIA_COMPONENT);
            } catch (BAExceptions.ActivityNotFound e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }
    }

    private void letvAutoStart() {
        if (isPackageExists(BrandPackage.LETV_MAIN)) {
            try {
                startAutoStartActivity(BrandPackage.LETV_MAIN, BrandPackage.LETV_COMPONENT);
            } catch (BAExceptions.ActivityNotFound e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }
    }

    private void asusAutoStart() {
        if (isPackageExists(BrandPackage.ASUS_MAIN)) {
            try {
                startAutoStartActivity(BrandPackage.ASUS_MAIN, BrandPackage.ASUS_COMPONENT);
            } catch (BAExceptions.ActivityNotFound e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }
    }

    private void honorAutoStart() {
        if (isPackageExists(BrandPackage.HONOR_MAIN)) {
            try {
                startAutoStartActivity(BrandPackage.HONOR_MAIN, BrandPackage.HONOR_COMPONENT);
            } catch (BAExceptions.ActivityNotFound e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }
    }

    private void oppoAutoStart() {
        if (isPackageExists(BrandPackage.OPPO_MAIN) || isPackageExists(BrandPackage.OPPO_FALLBACK)) {
            try {
                startAutoStartActivity(BrandPackage.OPPO_MAIN, BrandPackage.OPPO_COMPONENT);
            } catch (BAExceptions.ActivityNotFound e) {
                Log.e(TAG, e.getMessage(), e);
                try {
                    startAutoStartActivity(BrandPackage.OPPO_FALLBACK, BrandPackage.OPPO_COMPONENT_FALLBACK);
                } catch (BAExceptions.ActivityNotFound ex) {
                    Log.e(TAG, ex.getMessage(), ex);
                    try {
                        startAutoStartActivity(BrandPackage.OPPO_MAIN, BrandPackage.OPPO_COMPONENT_FALLBACK_A);
                    } catch (BAExceptions.ActivityNotFound exx) {
                        Log.e(TAG, exx.getMessage(), exx);
                    }
                }
            }
        }
    }

    private void vivoAutoStart() {
        if (isPackageExists(BrandPackage.VIVO_MAIN) || isPackageExists(BrandPackage.VIVO_FALLBACK)) {
            try {
                startAutoStartActivity(BrandPackage.VIVO_MAIN, BrandPackage.VIVO_COMPONENT);
            } catch (BAExceptions.ActivityNotFound e) {
                Log.e(TAG, e.getMessage(), e);
                try {
                    startAutoStartActivity(BrandPackage.VIVO_FALLBACK, BrandPackage.VIVO_COMPONENT_FALLBACK);
                } catch (BAExceptions.ActivityNotFound ex) {
                    Log.e(TAG, ex.getMessage(), ex);
                    try {
                        startAutoStartActivity(BrandPackage.VIVO_MAIN, BrandPackage.VIVO_COMPONENT_FALLBACK_A);
                    } catch (BAExceptions.ActivityNotFound exx) {
                        Log.e(TAG, exx.getMessage(), exx);
                    }
                }
            }
        }
    }

    private void startAutoStartActivity(String packageName, String componentName) throws BAExceptions.ActivityNotFound {
        Intent intentAutoStartPage = new Intent();
        intentAutoStartPage.setComponent(new ComponentName(packageName, componentName));
        intentAutoStartPage.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            _context.startActivity(intentAutoStartPage);
        } catch (Exception e) {
            throw new BAExceptions.ActivityNotFound();
        }
    }

    private boolean isPackageExists(String targetPackage) {
        PackageManager packageManager = _context.getPackageManager();
        for (ApplicationInfo packageInfo : packageManager.getInstalledApplications(0)) {
            if (packageInfo.packageName.equals(targetPackage)) {
                return true;
            }
        }
        return false;
    }

    private Brand getBrand() {
        // This method should return the current brand of the device.
        // For example, you could use Build.BRAND and convert it to the Brand enum.
        // This is a placeholder implementation.
        String brandName = Build.BRAND.toUpperCase();
        try {
            return Brand.valueOf(brandName);
        } catch (IllegalArgumentException e) {
            return Brand.OTHER;
        }
    }

    private enum Brand {
        REDMI,
        XIAOMI,
        NOKIA,
        LETV,
        ASUS,
        HONOR,
        OPPO,
        VIVO,
        OTHER
    }

    private static class BrandPackage {
        // Xiaomi
        private static final String XIAOMI_MAIN = "com.miui.securitycenter";
        private static final String XIAOMI_COMPONENT = "com.miui.permcenter.autostart.AutoStartManagementActivity";

        // Nokia
        private static final String NOKIA_MAIN = "com.evenwell.powersaving.g3";
        private static final String NOKIA_COMPONENT = "com.evenwell.powersaving.g3.exception.PowerSaverExceptionActivity";

        // Letv
        private static final String LETV_MAIN = "com.letv.android.letvsafe";
        private static final String LETV_COMPONENT = "com.letv.android.letvsafe.AutobootManageActivity";

        // ASUS ROG
        private static final String ASUS_MAIN = "com.asus.mobilemanager";
        private static final String ASUS_COMPONENT = "com.asus.mobilemanager.powersaver.PowerSaverSettings";

        // Honor
        private static final String HONOR_MAIN = "com.huawei.systemmanager";
        private static final String HONOR_COMPONENT = "com.huawei.systemmanager.optimize.process.ProtectActivity";

        // Oppo
        private static final String OPPO_MAIN = "com.coloros.safecenter";
        private static final String OPPO_FALLBACK = "com.oppo.safe";
        private static final String OPPO_COMPONENT = "com.coloros.safecenter.permission.startup.StartupAppListActivity";
        private static final String OPPO_COMPONENT_FALLBACK = "com.oppo.safe.permission.startup.StartupAppListActivity";
        private static final String OPPO_COMPONENT_FALLBACK_A = "com.coloros.safecenter.startupapp.StartupAppListActivity";

        // Vivo
        private static final String VIVO_MAIN = "com.iqoo.secure";
        private static final String VIVO_FALLBACK = "com.vivo.permissionmanager";
        private static final String VIVO_COMPONENT = "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity";
        private static final String VIVO_COMPONENT_FALLBACK = "com.vivo.permissionmanager.activity.BgStartUpManagerActivity";
        private static final String VIVO_COMPONENT_FALLBACK_A = "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager";
    }

    public static class BAExceptions {
        public static class ActivityNotFound extends Exception {
            public ActivityNotFound() {
                super("Activity not found");
            }
        }
    }
}
