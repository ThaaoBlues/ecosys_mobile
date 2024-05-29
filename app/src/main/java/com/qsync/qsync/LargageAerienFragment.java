package com.qsync.qsync;

import android.app.Activity;
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

import com.qsync.qsync.databinding.FragmentLargageAerienBinding;

import java.io.File;

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

                                BackendApi.addButtonsFromDevicesGenArray(
                                        getContext(),
                                        acces.getNetworkMap(),
                                        (LinearLayout) binding.appareilsLargageLinearlayout,
                                        new BackendApi.DeviceButtonCallback() {
                                            @Override
                                            public void callback(Map<String, String> device) {
                                                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                                                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                                                intent.addCategory(Intent.CATEGORY_OPENABLE);
                                                intent.setType("*/*");


                                                target_device = device;
                                                // Optionally, specify a URI for the file that should appear in the
                                                // system file picker when it loads.
                                                //intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, );

                                                selectFileLauncher.launch(intent);
                                            }
                                        }
                                );


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
                Networking nt = new Networking(getContext(), getContext().getFilesDir().toString());
                nt.sendLargageAerien(uri, target_device.get("ip_addr"),false);
            }
        };
        ProcessExecutor.startProcess(SendLA);
    }

    private void SendMultipleLA(Uri[] uris) {
        ProcessExecutor.Function SendLA = new ProcessExecutor.Function() {
            @Override
            public void execute() {
                Networking nt = new Networking(getContext(), getContext().getFilesDir().toString());
                File zipFile = new File(
                        getContext().getFilesDir(),
                        "/largage_aerien/mulilargage.zip");
                Log.d("Qsync Server","Zipping file...");

                FileZipper.zipFiles(getContext(),uris,zipFile.getPath());
                Log.d("Qsync Server","Zip file built !");

                nt.sendLargageAerien(Uri.fromFile(zipFile), target_device.get("ip_addr"),true);
                Log.d("Qsync Server","Sending multiple largage aerien :"+zipFile.getName());
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