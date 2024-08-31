/*
 * *
 *  * Created by Théo Mougnibas on 27/06/2024 17:18
 *  * Copyright (c) 2024 . All rights reserved.
 *  * Last modified 27/06/2024 17:18
 *
 */

package com.ecosys.ecosys;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class TextViewActivity extends AppCompatActivity {

    private static String TAG = "Ecosys Server : TextViewActivity";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_text_view);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });


        Intent intent = getIntent();
        if (intent != null) {
            // Retrieve the flag and app name from the intent
            String file_path = intent.getStringExtra("file_path");
            readFIleToTextView(file_path);


        }
    }


    private void readFIleToTextView(String filePath){
        //DocumentFile f = DocumentFile.fromSingleUri(TextViewActivity.this,Uri.parse(file_path));
        File f = new File(filePath);
        // Display the received values (you can handle them as needed)
        EditText textView = findViewById(R.id.editTextTextMultiLine);

        try {
            FileInputStream fis = new FileInputStream(f);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                textView.setText(new String(fis.readAllBytes()));
            }
            fis.close();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent); // Update the intent


        if (intent != null) {
            // Retrieve the flag and app name from the intent
            String file_path = intent.getStringExtra("file_path");
            Log.d(TAG, "Received URI in onNewIntent: " + file_path);
            readFIleToTextView(file_path);

        }
     }



}