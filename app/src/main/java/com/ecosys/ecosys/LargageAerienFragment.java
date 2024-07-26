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
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.ecosys.ecosys.databinding.FragmentLargageAerienBinding;

import java.io.File;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;


public class LargageAerienFragment extends Fragment {

    private FragmentLargageAerienBinding binding;

    private ActivityResultLauncher<Intent> selectFileLauncher;

    private Map<String,String> target_device;
    //private ZeroConfService zc = new ZeroConfService(getContext());

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentLargageAerienBinding.inflate(inflater, container, false);


        return binding.getRoot();

    }



    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        binding.buttonLargageVersSynchronisations.setOnClickListener(v ->
                NavHostFragment.findNavController(LargageAerienFragment.this)
                        .navigate(R.id.action_FirstFragment_to_SecondFragment)
        );




        selectFileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            // There are no request codes
                            Intent data = result.getData();
                            if (result.getResultCode() == Activity.RESULT_OK) {
                                if (data != null) {
                                    // Check if multiple files were selected
                                    if (data.getClipData() != null) {
                                        // Handle multiple files

                                        int count = data.getClipData().getItemCount();
                                        Uri[] queue = new Uri[count];
                                        for (int i = 0; i < count; i++) {
                                            Uri uri = data.getClipData().getItemAt(i).getUri();
                                            queue[i] = uri;
                                        }
                                        SendMultipleLA(queue);

                                    } else if (data.getData() != null) {
                                        // Handle single file
                                        Uri uri = data.getData();
                                        SendLA(uri);
                                    }
                                }
                            }

                        }
                    }
                });


        ProcessExecutor.Function updateDeviceList = new ProcessExecutor.Function() {
            @Override
            public void execute() {

                AccesBdd acces = new AccesBdd(getContext());
                while(true){

                    if(LargageAerienFragment.this.isVisible()){

                        ProcessExecutor.Function upui = new ProcessExecutor.Function(){

                            @Override
                            public void execute() {


                                try{
                                    BackendApi.addButtonsFromDevicesGenArray(
                                            getContext(),
                                            acces.getNetworkMap(),
                                            (LinearLayout) binding.appareilsLargageLinearlayout,
                                            new BackendApi.DeviceButtonCallback() {
                                                @Override
                                                public void callback(Map<String, String> device) {


                                                    String[] choices = {"Send files","Send some text"};
                                                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());

                                                    builder.setTitle("Select an action");

                                                    builder.setItems(choices, new DialogInterface.OnClickListener() {
                                                        @Override
                                                        public void onClick(DialogInterface dialog, int which) {
                                                            target_device = device;

                                                            switch (which){
                                                                case 0:
                                                                    dialog.dismiss();
                                                                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                                                                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                                                                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                                                                    intent.setType("*/*");


                                                                    // Optionally, specify a URI for the file that should appear in the
                                                                    // system file picker when it loads.
                                                                    //intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, );

                                                                    selectFileLauncher.launch(intent);

                                                                    break;
                                                                case 1:
                                                                    dialog.dismiss();


                                                                    ProcessExecutor.Function aski = new ProcessExecutor.Function() {
                                                                        @Override
                                                                        public void execute() {
                                                                            String text = BackendApi.askMultilineInput(
                                                                                    "TYPE YOUR TEXT HERE",
                                                                                    "Type/paste the text you want to send here",
                                                                                    getContext()
                                                                            );

                                                                            try {
                                                                                File outputFile = File.createTempFile(
                                                                                        "text",
                                                                                        ".txt",
                                                                                        getContext().getCacheDir()
                                                                                );

                                                                                FileOutputStream fos = new FileOutputStream(outputFile);

                                                                                fos.write(text.getBytes());

                                                                                fos.close();
                                                                                Log.d("Ecosys Server : Largage Aerien Fragment",Uri.parse(outputFile.getPath()).toString());
                                                                                SendLA(Uri.parse(outputFile.getPath()));

                                                                            } catch (IOException e) {
                                                                                throw new RuntimeException(e);
                                                                            }
                                                                        }
                                                                    };

                                                                    ProcessExecutor.startProcess(aski);

                                                                    break;




                                                                default:
                                                                    break;

                                                            }
                                                        }
                                                    });

                                                    builder.show();



                                                }
                                            }
                                    );
                                }catch (java.lang.NullPointerException ignored){

                                }



                            }
                        };
                        ProcessExecutor.executeOnUIThread(upui);
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }








            }
        };

        ProcessExecutor.startProcess(updateDeviceList);




    }


    private void SendLA(Uri uri) {
        ProcessExecutor.Function SendLA = new ProcessExecutor.Function() {
            @Override
            public void execute() {

                BackendApi.showLoadingNotification(getContext(),"Sending Largage Aerien...");
                Networking nt = new Networking(getContext(), getContext().getFilesDir().toString());
                nt.sendLargageAerien(uri, target_device.get("ip_addr"),false);
                BackendApi.discardLoadingNotification(getContext());
            }
        };
        ProcessExecutor.startProcess(SendLA);
    }

    private void SendMultipleLA(Uri[] uris) {
        ProcessExecutor.Function SendLA = new ProcessExecutor.Function() {
            @Override
            public void execute() {

                BackendApi.showLoadingNotification(getContext(),"Sending Largage Aerien...");

                Networking nt = new Networking(getContext(), getContext().getFilesDir().toString());
                File tarFile = new File(
                        getContext().getFilesDir(),
                        "/largage_aerien/multilargage.tar");
                Log.d("Ecosys Server","tarring file...");

                FileTar.tarFiles(getContext(),uris,tarFile.getPath());
                Log.d("Ecosys Server","tar file built !");

                nt.sendLargageAerien(Uri.fromFile(tarFile), target_device.get("ip_addr"),true);
                Log.d("Ecosys Server","Sending multiple largage aerien :"+tarFile.getName());

                BackendApi.discardLoadingNotification(getContext());
            }
        };
        ProcessExecutor.startProcess(SendLA);
    }



    @Override
    public void onDestroyView() {
        super.onDestroyView();
        //zc.shutdown();
        binding = null;
    }

}