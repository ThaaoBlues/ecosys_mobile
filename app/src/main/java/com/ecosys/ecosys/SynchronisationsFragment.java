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
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.ecosys.ecosys.databinding.FragmentSynchronisationsBinding;

import java.util.Arrays;
import java.util.Map;

public class SynchronisationsFragment extends Fragment {

    private FragmentSynchronisationsBinding binding;
    private ActivityResultLauncher<Intent> selectFolderLauncher;


    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentSynchronisationsBinding.inflate(inflater, container, false);
        return binding.getRoot();

    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.buttonSychroVersMagasin.setOnClickListener(v ->
                NavHostFragment.findNavController(SynchronisationsFragment.this)
                        .navigate(R.id.action_SynchronisationsFragment_to_fragment_magasin)
        );

        binding.buttonSynchroVersLargageAerien.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NavHostFragment.findNavController(SynchronisationsFragment.this)
                        .navigate(R.id.action_SynchronisationsFragment_to_LargageAerienFragment);

            }
        });


        selectFolderLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            // There are no request codes
                            Intent data = result.getData();
                            if (result.getResultCode() == Activity.RESULT_OK) {
                                if (data.getData() != null) {

                                    //Log.d("SynchronisationsFragment","Uri : "+ PathUtils.getPathFromUri(getContext(),data.getData()));
                                    Uri treeUri = data.getData();

                                    getContext().getContentResolver().takePersistableUriPermission(treeUri,
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                                    DocumentFile directory = DocumentFile.fromTreeUri(getContext(), treeUri);
                                    if (directory != null && directory.isDirectory()) {
                                        AccesBdd acces = new AccesBdd(getContext());

                                        acces.createSync(directory.getUri().toString());
                                        FileSystem.startDirectoryMonitoring(getContext(),directory,acces.getSecureId());

                                        acces.closedb();

                                    }
                                }
                            }

                        }
                    }
                });


        AccesBdd acces = new AccesBdd(getContext());

        Map<String, Globals.SyncInfos> synchros = acces.listSyncAllTasks();
        acces.closedb();

        BackendApi.addButtonsFromSynchroGenArray(
                getContext(),
                syncMapToGenArray(synchros),
                binding.listeSynchronisationsLinearLayout,
                new SynchronisationButtonCallback(){

                    @Override
                    public void callback(Globals.SyncInfos sync) {

                        AccesBdd acces = new AccesBdd(getContext());
                        acces.setSecureId(sync.getSecureId());


                        LayoutInflater inflater = getLayoutInflater();
                        View dialogView = inflater.inflate(R.layout.buttons_dialog_custom_layout, null);

                        // Build the alert dialog
                        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(),R.style.TransparentDialogStyle);
                        builder.setView(dialogView);


                        // Create the dialog
                        AlertDialog alertDialog = builder.create();

                        alertDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

                        // set transparent background to not shit on the rounded corners of the layout
                        alertDialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
                        alertDialog.getWindow().setDimAmount(0);

                        TextView title = dialogView.findViewById(R.id.buttons_dialog_title);
                        title.setText(R.string.select_an_action);

                        TextView msg = dialogView.findViewById(R.id.buttons_dialog_message);
                        msg.setText(R.string.select_something_to_do_with_this_synchronisation_task);


                        LinearLayout buttonsLayout = dialogView.findViewById(R.id.dialog_buttons_layout);
                        float widthInPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PT, 100, getResources().getDisplayMetrics());


                        // Create the first button (for the first choice)
                        Button rmTaskButton = new Button(getContext());
                        rmTaskButton.setLayoutParams(new LinearLayout.LayoutParams((int) widthInPx, LinearLayout.LayoutParams.WRAP_CONTENT)); 
                        rmTaskButton.setText("Delete Task");
                        rmTaskButton.setTextColor(ContextCompat.getColor(getContext(),R.color.bg1));
                        rmTaskButton.setBackgroundColor(ContextCompat.getColor(getContext(),R.color.btn1));
                        rmTaskButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                acces.rmSync();
                                alertDialog.dismiss();
                            }
                        });



                        Button sendLinkRequestButton = new Button(getContext());
                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams((int) widthInPx, LinearLayout.LayoutParams.WRAP_CONTENT);
                        params.topMargin = 20;
                        sendLinkRequestButton.setLayoutParams(params);
                        sendLinkRequestButton.setText(R.string.link_this_task_to_another_device);
                        sendLinkRequestButton.setTextColor(ContextCompat.getColor(getContext(),R.color.bg1));
                        sendLinkRequestButton.setBackgroundColor(ContextCompat.getColor(getContext(),R.color.btn1));
                        sendLinkRequestButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {

                                // link another device to the designated sync task

                                Globals.GenArray<Map<String,String>> devices = acces.getNetworkMap();

                                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                                builder.setTitle("Online devices");
                                String[] choices = new String[devices.size()];

                                for(int i=0;i<devices.size();i++){

                                    choices[i] = devices.get(i).get("hostname");

                                }




                                builder.setItems(choices, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int i) {


                                        ProcessExecutor.Function sendreqs = new ProcessExecutor.Function() {
                                            @Override
                                            public void execute() {
                                                BackendApi.showLoadingNotification(getContext(),"Negociating link between devices...");
                                                String ip_addr = devices.get(i).get("ip_addr");
                                                String device_id = devices.get(i).get("device_id");
                                                Networking.sendLinkDeviceRequest(ip_addr,acces);
                                                acces.linkDevice(device_id,ip_addr);

                                                BackendApi.discardLoadingNotification(getContext());
                                            }
                                        };

                                        ProcessExecutor.startProcess(sendreqs);

                                    }
                                });

                                builder.show();
                            }
                        });



                        // Create the first button (for the first choice)
                        Button backupModeButton = new Button(getContext());
                        // same params as precedent button
                        backupModeButton.setLayoutParams(params);
                        backupModeButton.setText(sync.getBackup_mode() ? "Disable backup mode":  "Enable backup mode");
                        backupModeButton.setTextColor(ContextCompat.getColor(getContext(),R.color.bg1));
                        backupModeButton.setBackgroundColor(ContextCompat.getColor(getContext(),R.color.btn1));
                        backupModeButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                acces.toggleBackupMode();
                            }
                        });

                        // Add buttons to the linear layout
                        buttonsLayout.addView(rmTaskButton);
                        buttonsLayout.addView(sendLinkRequestButton);
                        buttonsLayout.addView(backupModeButton);
                        builder.show();
                    }
                }
        );



        binding.buttonCreerSynchronisation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                selectFolderLauncher.launch(intent);
            }
        });







    }


    public interface SynchronisationButtonCallback{
        void callback(Globals.SyncInfos sync);
    }





    public static Globals.GenArray syncMapToGenArray(Map<String, Globals.SyncInfos> map){

        Globals.GenArray<Globals.SyncInfos> ret = new Globals.GenArray<>();
        map.forEach((k,v)->ret.add(v));
        return ret;

    }

    @Override
    public void onDestroyView() {

        super.onDestroyView();
        binding = null;
    }

}