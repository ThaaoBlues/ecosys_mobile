package com.qsync.qsync;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;

import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.qsync.qsync.databinding.ActivityAppsIntentBinding;

public class AppsIntentActivity extends AppCompatActivity {

    private AppBarConfiguration appBarConfiguration;
    private ActivityAppsIntentBinding binding;
    private final String TAG = "Qsync Server : AppsIntentActivity";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityAppsIntentBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_apps_intent);
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        Intent intent = getIntent();
        if (intent != null) {
            // Retrieve the flag and app name from the intent
            String actionFlag = intent.getStringExtra("action_flag");
            String appName = intent.getStringExtra("app_name");

            // Display the received values (you can handle them as needed)
            TextView textView = findViewById(R.id.textView);
            textView.setText("Action Flag: " + actionFlag + "\nApp Name: " + appName);

            if(actionFlag == "[INSTALL]"){
                // create app folder
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, appName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "vnd.android.document/directory");
                Uri folderUri = Uri.parse("content://" + "com.qsync.fileprovider" + "/" + appName);

                Uri newFolderUri = getContentResolver().insert(folderUri, values);

                if (newFolderUri != null) {
                    Log.d("Qsync ", "Folder created successfully: " + newFolderUri);
                } else {
                    Log.e(TAG, "Failed to create folder: " + appName);
                }


                // create a sync in it
                AccesBdd acces = new AccesBdd(AppsIntentActivity.this);

                acces.createSync(folderUri.getPath());

            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_apps_intent);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }
}