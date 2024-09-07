/*
 * *
 *  * Created by Théo Mougnibas on 27/06/2024 17:18
 *  * Copyright (c) 2024 . All rights reserved.
 *  * Last modified 27/06/2024 17:18
 *
 */

package com.ecosys.ecosys;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import androidx.core.app.ActivityCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.ecosys.ecosys.databinding.ActivityMainBinding;

import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import java.io.File;
import java.security.Permission;

public class MainActivity extends AppCompatActivity {

    private static final int MY_PERMISSIONS_REQUEST_POST_NOTIFICATIONS = 2;
    private static final int MY_PERMISSIONS_REQUEST_SYSTEM_ALERT_WINDOW = 3;
    private static final int MY_PERMISSIONS_REQUEST_RECEIVE_BOOT_COMPLETED = 4;

    private static final String TAG = "Ecosys : MainActivity";
    private SharedPreferences prefs;




    public void requestPermission(final Activity activity, final String permission, final int requestCode, final String msg) {
        Log.d(TAG,"Requestion permission if not granted : "+permission);

        if (ActivityCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {

            if(requestCode == MY_PERMISSIONS_REQUEST_SYSTEM_ALERT_WINDOW){
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                startActivity (intent);
            }

            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                // Show an explanation to the user why you need the permission
                // After the user sees the explanation, try again to request the permission
                new AlertDialog.Builder(this)
                        .setTitle("Permission Needed")
                        .setMessage(msg)
                        .setPositiveButton("OK", (dialog, which) -> {
                            ActivityCompat.requestPermissions(this,
                                    new String[]{permission},
                                    requestCode);
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                        .create()
                        .show();
            } else {
                // No explanation needed, request the permission
                ActivityCompat.requestPermissions(this,
                        new String[]{permission},
                        requestCode);
            }
        } else {
            Log.d(TAG,"Permission already granted");
            // Permission has already been granted
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch(requestCode){


            case MY_PERMISSIONS_REQUEST_POST_NOTIFICATIONS:

                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {


                } else {
                    // Permission denied
                }
                break;

            case MY_PERMISSIONS_REQUEST_SYSTEM_ALERT_WINDOW:
                break;

            case MY_PERMISSIONS_REQUEST_RECEIVE_BOOT_COMPLETED:
                break;
        }
    }

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        /*setSupportActionBar(binding.toolbar);

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        */

        requestPermission(
                MainActivity.this,
                "android.permission.POST_NOTIFICATIONS",
                MY_PERMISSIONS_REQUEST_POST_NOTIFICATIONS,
                "This app needs the Notification permission to show you when it is talking to your others devices."
        );
        /*requestPermission(
                MainActivity.this,
                "android.permission.SYSTEM_ALERT_WINDOW",
                MY_PERMISSIONS_REQUEST_SYSTEM_ALERT_WINDOW,
                "This app needs the system alert permission to ask your approval when you receive a Largage Aerien."
        );*/

        requestPermission(
                MainActivity.this,
                "android.permission.RECEIVE_BOOT_COMPLETED",
                MY_PERMISSIONS_REQUEST_RECEIVE_BOOT_COMPLETED,
                "This app needs the permission to know if your device has booted to start itself."
        );



        prefs = getSharedPreferences("com.ecosys.ecosys", MODE_PRIVATE);

        if(!prefs.getBoolean("autostart",false)){
            AutoStartPermissionManager as = new AutoStartPermissionManager(MainActivity.this);
            as.requestAutoStartPermission();
            prefs.edit().putBoolean("autostart",true).apply();
        }


        //prefs.edit().putBoolean("firstrun", true).commit();
        //deleteDatabase("ecosys");




        Intent intent = new Intent(MainActivity.this,SelectorActivity.class);
        intent.putExtra("flag","[MAKE_SURE_SERVERS_ARE_RUNNING]");
        startActivity(intent);



        /*if(!ProcessExecutor.isMyServiceRunning(MainActivity.this,StartupService.class)){
            Log.d(TAG,"Service was not running at application startup !! Starting it now");
            Intent intent = new Intent(this, StartupService.class);
            startService(intent);
            finish();
        }*/


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();


        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }


        @Override
        protected void onResume() {
            super.onResume();

            if (prefs.getBoolean("firstrun", true)) {

                Intent myIntent = new Intent(MainActivity.this, BienvenueActivity.class);
                startActivity(myIntent);
                prefs.edit().putBoolean("firstrun", false).commit();
            }
        }



}