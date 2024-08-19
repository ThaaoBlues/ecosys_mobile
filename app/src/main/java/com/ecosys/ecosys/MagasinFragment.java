/*
 * *
 *  * Created by Théo Mougnibas on 27/06/2024 17:18
 *  * Copyright (c) 2024 . All rights reserved.
 *  * Last modified 27/06/2024 17:18
 *
 */

package com.ecosys.ecosys;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.fragment.NavHostFragment;

import com.bumptech.glide.Glide;
import com.ecosys.ecosys.databinding.FragmentMagasinBinding;
import com.ecosys.ecosys.databinding.FragmentSynchronisationsBinding;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MagasinFragment extends Fragment {

    private static String TAG = "Qsycn Server : MagasinFragment";
    private FragmentMagasinBinding binding;
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        binding = FragmentMagasinBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        binding.buttonMagasinVersLargageAerien.setOnClickListener(v ->
                NavHostFragment.findNavController(MagasinFragment.this)
                        .navigate(R.id.action_fragment_magasin_to_LargageAerienFragment)
        );

        binding.buttonMagasinVersSynchronisations.setOnClickListener(v ->
                NavHostFragment.findNavController(MagasinFragment.this)
                        .navigate(R.id.action_fragment_magasin_to_SynchronisationsFragment)
        );


        fetchConfig(binding.fragmentMagasin);



    }

    private void fetchConfig(View view) {
        new Thread(() -> {
            try {
                URL url = new URL("https://raw.githubusercontent.com/ThaaoBlues/ecosys/master/magasin_database.json");
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                BufferedReader reader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));

                StringBuilder json = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    json.append(line);
                }
                reader.close();

                JSONObject data = new JSONObject(json.toString());
                //Log.d(TAG,data.get("tout_en_un_configs").toString());
                JSONArray arr = data.getJSONArray("tout_en_un_configs");
                getActivity().runOnUiThread(() -> fillMagasin(view, getContext(),arr));

            } catch (Exception e) {
                Log.e(TAG, "Error fetching config file", e);
            }
        }).start();
    }

    private void fillMagasin(View view, Context context, JSONArray data) {
        try {
            LinearLayout container = view.findViewById(R.id.container);

            for (int i = 0; i < data.length(); i++) {
                JSONObject config = data.getJSONObject(i);
                //Log.d(TAG,config.getJSONArray("SupportedPlatforms").toString());
                if (config.getJSONArray("SupportedPlatforms").toString().contains("Android")) {
                    View card = LayoutInflater.from(context).inflate(R.layout.card_layout, container, false);

                    ImageView image = card.findViewById(R.id.app_image);
                    TextView title = card.findViewById(R.id.card_title);
                    TextView description = card.findViewById(R.id.card_description);
                    Button installButton = card.findViewById(R.id.install_button);

                    Glide.with(this).load(config.getString("AppIconURL")).into(image);
                    title.setText(config.getString("AppName"));
                    description.setText(config.getString("AppDescription"));

                    String appDownloadUrl = config.getString("AppDownloadUrl");
                    installButton.setOnClickListener(v -> {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(Uri.parse(appDownloadUrl));
                        startActivity(intent);
                    });

                    container.addView(card);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing JSON", e);
        }
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }



}