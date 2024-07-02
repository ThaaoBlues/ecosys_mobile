/*
 * *
 *  * Created by Théo Mougnibas on 27/06/2024 17:18
 *  * Copyright (c) 2024 . All rights reserved.
 *  * Last modified 27/06/2024 17:18
 *
 */

package com.qsync.qsync;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.qsync.qsync.databinding.FragmentSynchronisationsBinding;

import java.lang.reflect.Array;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
                                        FileSystem.startDirectoryMonitoring(getContext(),directory);

                                        acces.closedb();
                                    }
                                }
                            }

                        }
                    }
                });


        AccesBdd acces = new AccesBdd(getContext());

        Map<String, Globals.SyncInfos> synchros = acces.ListSyncAllTasks();
        acces.closedb();

        BackendApi.addButtonsFromSynchroGenArray(
                getContext(),
                syncMapToGenArray(synchros),
                binding.listeSynchronisationsLinearLayout,
                new SynchronisationButtonCallback(){

                    @Override
                    public void callback(Globals.SyncInfos sync) {

                        AccesBdd acces = new AccesBdd(getContext());
                        acces.SetSecureId(sync.getSecureId());

                        String[] choices = {"Delete task", "See devices status", "Link another device to this task"};
                        choices = Arrays.copyOf(choices,choices.length+1);

                        if(!sync.getBackup_mode()){
                            choices[choices.length-1] = "Enable backup mode";
                        }else{
                            choices[choices.length-1] = "Disable backup mode";

                        }

                        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                        builder.setTitle("Sync Options");
                        String[] finalChoices = choices;
                        builder.setItems(choices, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int choice_index) {


                                acces.SetSecureId(sync.getSecureId());

                                switch (choice_index){
                                    case 0:
                                        acces.RmSync();
                                        break;

                                    case 1:
                                        // get task status -\_(:/)_/-
                                        break;
                                    case 2:
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
                                                        acces.LinkDevice(device_id,ip_addr);

                                                        BackendApi.discardLoadingNotification(getContext());
                                                    }
                                                };

                                                ProcessExecutor.startProcess(sendreqs);

                                            }
                                        });

                                        builder.show();

                                        break;
                                    case 3:
                                        acces.toggleBackupMode();

                                        break;

                                }

                                Toast.makeText(getContext(), finalChoices[choice_index], Toast.LENGTH_SHORT).show();

                            }
                        });

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