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

import java.nio.file.Path;
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

        AccesBdd acces = new AccesBdd(getContext());


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
                                    /*acces.createSync(getContext().getContentResolver().query(
                                            data.getData(),
                                            null,
                                            null,
                                            null,
                                            null
                                    ).getColumnIndex(OpenableColumns.));*/

                                    //Log.d("SynchronisationsFragment","Uri : "+ PathUtils.getPathFromUri(getContext(),data.getData()));
                                    Uri treeUri = data.getData();
                                    DocumentFile directory = DocumentFile.fromTreeUri(getContext(), treeUri);
                                    if (directory != null && directory.isDirectory()) {
                                        acces.createSync(directory.getUri().toString());
                                        FileSystem.startDirectoryMonitoring(getContext(),directory);
                                    }
                                }
                            }

                        }
                    }
                });



        Map<String, Globals.SyncInfos> synchros = acces.ListSyncAllTasks();
        addButtonsFromSynchroGenArray(
                getContext(),
                syncMapToGenArray(synchros),
                binding.listeSynchronisationsLinearLayout,
                new SynchronisationButtonCallback(){

                    @Override
                    public void callback(Globals.SyncInfos sync) {
                        String[] choices = {"Delete task", "See devices status"};

                        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                        builder.setTitle("");
                        builder.setItems(choices, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int choice_index) {

                                Toast.makeText(getContext(), choices[choice_index], Toast.LENGTH_SHORT).show();
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


    public static void addButtonsFromSynchroGenArray(Context context, Globals.GenArray<Globals.SyncInfos> synchros, LinearLayout linearLayout, SynchronisationButtonCallback callback) {

        // will produce erros when user is not on the linear layout parent's fragment
        try{
            linearLayout.removeAllViews();
        }catch (java.lang.NullPointerException e){

            return;
        }


        for (int i = 0;i<synchros.size();i++) {
            Button button = new Button(context);
            button.setText(synchros.get(i).getPath());
            button.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            int finalI = i;
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    callback.callback(synchros.get(finalI));
                }
            });
            linearLayout.addView(button);
            //Log.d("BACKEND API","Adding button for device : "+devices.get(i).toString());
        }


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