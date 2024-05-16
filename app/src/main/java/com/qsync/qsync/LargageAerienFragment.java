package com.qsync.qsync;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
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

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.util.Map;

public class LargageAerienFragment extends Fragment {

    private FragmentLargageAerienBinding binding;
    private static final int PERMISSION_REQUEST_CODE = 456;

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
                                    Uri uri = data.getData();
                                    //String filePath = getPathFromUri(uri);
                                    //Toast.makeText(this, "Selected file path: " + filePath, Toast.LENGTH_SHORT).show();

                                    ProcessExecutor.Function SendLA = new ProcessExecutor.Function() {
                                        @Override
                                        public void execute() {


                                            Networking nt = new Networking(getContext(),getContext().getFilesDir().toString());
                                            /*ParcelFileDescriptor parcelFileDescriptor =
                                                    null;
                                            try {
                                                parcelFileDescriptor = getContext().getContentResolver().openFileDescriptor(uri, "r");
                                            } catch (FileNotFoundException e) {
                                                throw new RuntimeException(e);
                                            }
                                            FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();*/

                                            nt.sendLargageAerien(uri,"127.0.0.1");




                                        }
                                    };

                                    ProcessExecutor.startProcess(SendLA);

                                }
                            }

                        }
                    }
                });

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
                                            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                                            intent.addCategory(Intent.CATEGORY_OPENABLE);
                                            intent.setType("*/*");

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
        };

        ProcessExecutor.startProcess(updateDeviceList);




    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}