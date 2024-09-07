/*
 * *
 *  * Created by Théo Mougnibas on 27/06/2024 17:18
 *  * Copyright (c) 2024 . All rights reserved.
 *  * Last modified 27/06/2024 17:18
 *
 */

package com.ecosys.ecosys;

import static org.apache.commons.lang3.ClassUtils.getPackageName;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.fragment.NavHostFragment;

import com.bumptech.glide.Glide;
import com.ecosys.ecosys.databinding.FragmentMagasinBinding;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MagasinFragment extends Fragment {

    private static String TAG = "Qsycn Server : MagasinFragment";
    private FragmentMagasinBinding binding;
    private Uri apkUri;


    private ActivityResultLauncher<Intent> unknownSourcesLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    // Si l'utilisateur a activé l'autorisation, lancer l'installation
                    installApk(apkUri);
                } else {
                    Log.e(TAG, "L'autorisation d'installation depuis des sources inconnues a été refusée");
                }
            }
    );


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

                        Toast.makeText(context, "Downloading your app...", Toast.LENGTH_LONG).show();
                        downloadApkAndPromptInstall(appDownloadUrl);


                    });

                    container.addView(card);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing JSON", e);
        }
    }






    public void downloadApkAndPromptInstall(String fileUrl){

        new Thread(() -> {
            try {
                URL url = new URL(fileUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setDoOutput(true);
                connection.connect();

                // Créer le fichier
                File packageFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "ecosys_magasin_app.apk");
                if(!packageFile.exists()){
                    if(!packageFile.createNewFile()){
                        Log.d(TAG,"WTF ????");
                        throw new IOException();
                    }
                }
                FileOutputStream fileOutput = new FileOutputStream(packageFile);

                // Lire la réponse
                InputStream inputStream = new BufferedInputStream(url.openStream(), 8192);
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    fileOutput.write(buffer, 0, bytesRead);
                }

                fileOutput.flush();
                fileOutput.close();
                inputStream.close();

                Log.d(TAG, "File downloaded: " + packageFile.getAbsolutePath());

                // Utiliser FileProvider pour éviter l'exception FileUriExposedException
                apkUri = FileProvider.getUriForFile(
                        getContext(),
                        "com.ecosys.ecosys.fileprovider",
                        packageFile
                );

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {


                    if (!getContext().getPackageManager().canRequestPackageInstalls()) {
                        // Lancer l'intention pour autoriser les sources inconnues
                        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                                .setData(Uri.parse("package:" + getContext().getPackageName()));
                        unknownSourcesLauncher.launch(intent); // Utiliser ActivityResultLauncher
                    } else {
                        installApk(apkUri); // Si l'autorisation est déjà activée
                    }
                } else {
                    installApk(apkUri); // Pour les versions inférieures à Android 8.0
                }


            } catch (IOException e) {
                Log.e(TAG, "Error: " + e.getMessage());
            }





        }).start();
    }


    private void installApk(Uri apkUri){
        Log.d(TAG,"Prompting user to install app");
        Intent promptInstall = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(this.apkUri,
                        "application/vnd.android.package-archive");

        promptInstall.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        promptInstall.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);



        startActivity(promptInstall);
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }






}