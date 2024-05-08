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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.qsync.qsync.databinding.FragmentLargageAerienBinding;

import java.util.Map;

public class LargageAerienFragment extends Fragment {

    private FragmentLargageAerienBinding binding;
    private static final int PERMISSION_REQUEST_CODE = 456;

    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<Intent> selectFileLauncher;

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

        ZeroConfService zc = new ZeroConfService(getContext());


        ProcessExecutor.Function updateDeviceList = new ProcessExecutor.Function() {
            @Override
            public void execute() {
                //wait(1000);
                AccesBdd acces = new AccesBdd(getContext());

                while(true){

                    ProcessExecutor.Function upui = new ProcessExecutor.Function(){

                        @Override
                        public void execute() {

                            BackendApi.addButtonsFromDevicesGenArray(
                                    getContext(),
                                    zc.getConnectedDevices(),
                                    (LinearLayout) binding.appareilsLargageLinearlayout,
                                    new BackendApi.ButtonCallback() {
                                        @Override
                                        public void callback(Map<String, String> device) {

                                            try{

                                                selectFileLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                                                    if (result.getResultCode() == Activity.RESULT_OK) {
                                                        Intent data = result.getData();
                                                        if (data != null) {
                                                            Uri uri = data.getData();
                                                            //String filePath = getPathFromUri(uri);
                                                            //Toast.makeText(this, "Selected file path: " + filePath, Toast.LENGTH_SHORT).show();

                                                            ProcessExecutor.Function SendLA = new ProcessExecutor.Function() {
                                                                @Override
                                                                public void execute() {


                                                                    Networking nt = new Networking(getContext(),getContext().getFilesDir().toString());

                                                                    nt.sendLargageAerien(uri.getPath(),"127.0.0.1");

                                                                }
                                                            };

                                                            ProcessExecutor.startProcess(SendLA);

                                                        }
                                                    }
                                                });




                                            }catch (Error e){
                                                Log.wtf("Erreur en initialisant la BDD",e);
                                            }

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
        };

        ProcessExecutor.startProcess(updateDeviceList);




    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}