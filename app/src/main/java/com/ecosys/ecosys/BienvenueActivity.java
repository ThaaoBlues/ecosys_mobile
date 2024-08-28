/*
 * *
 *  * Created by Théo Mougnibas on 04/07/2024 18:33
 *  * Copyright (c) 2024 . All rights reserved.
 *  * Last modified 04/07/2024 18:13
 *
 */

package com.ecosys.ecosys;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class BienvenueActivity extends AppCompatActivity {


    private int step =0;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_bienvenue);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });



        BienvenueActivity.this.findViewById(R.id.button_ok_bienvenue).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                step ++;
                ImageView img = BienvenueActivity.this.findViewById(R.id.imageView_bienvenue);
                TextView msg = BienvenueActivity.this.findViewById(R.id.textview_bienvenue);
                switch (step){
                    case 1:

                        img.setImageDrawable(
                                getDrawable(R.drawable.largage)
                        );


                        msg.setText(R.string.largage_aerien_explication);
                        break;
                    case 2:

                        img.setImageDrawable(
                                getDrawable(R.drawable.test)
                        );
                        msg.setText(R.string.sync_task_explication);
                        break;

                    case 3:

                        img.setImageDrawable(
                                getDrawable(R.drawable.magasin)
                        );
                        msg.setText(R.string.magasin_explication);
                        break;
                    case 4:
                        img.setImageDrawable(
                                getDrawable(R.drawable.backup)
                        );
                        msg.setText(R.string.backup_mode_explication);
                        break;
                    case 5:
                        img.setImageDrawable(getDrawable(R.drawable.settings));
                        msg.setText(R.string.dont_forget_to_enable_ecosys_autostart_in_your_settings_so_it_can_talk_to_your_devices_without_you_even_knowing_it);
                        break;
                    case 6:
                        Intent myIntent = new Intent(BienvenueActivity.this, MainActivity.class);
                        startActivity(myIntent);

                }

            }
        });
    }
}