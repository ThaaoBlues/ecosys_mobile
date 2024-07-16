package com.qsync.qsync;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.util.Map;

public class SelectAppToLinkActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_select_app_to_link);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });



        //verify that the apps folder is present !
        DocumentFile appsFolder = DocumentFile.fromFile(new File(getFilesDir(),"apps"));

        if(!appsFolder.exists()){
            appsFolder.getParentFile().createDirectory("apps");
        }


        AccesBdd acces = new AccesBdd(SelectAppToLinkActivity.this);
        Map<String, Globals.SyncInfos> synchros = acces.listSyncAllTasks();
        acces.closedb();
        Globals.GenArray<Globals.SyncInfos> syncInfosGenArray = syncMapToGenArray(synchros);
        Globals.GenArray<Globals.SyncInfos> appsInfosGenArray = new Globals.GenArray<>();
        // remove all the sync that are not associated to an app
        for(int i = 0;i<syncInfosGenArray.size();i++){
            if(syncInfosGenArray.get(i).isApp()){
                appsInfosGenArray.add(syncInfosGenArray.get(i));
            }
        }

        BackendApi.addButtonsFromSynchroGenArray(
                SelectAppToLinkActivity.this,
                appsInfosGenArray,
                SelectAppToLinkActivity.this.findViewById(R.id.select_app_to_link_linearlayout),
                new SynchronisationsFragment.SynchronisationButtonCallback(){

                    @Override
                    public void callback(Globals.SyncInfos sync) {

                        AccesBdd acces = new AccesBdd(SelectAppToLinkActivity.this);
                        acces.setSecureId(sync.getSecureId());

                        acces.updateSyncId(sync.getPath(),sync.getSecureId());
                        finish();

                    }
                }
        );
    }


    public static Globals.GenArray syncMapToGenArray(Map<String, Globals.SyncInfos> map){

        Globals.GenArray<Globals.SyncInfos> ret = new Globals.GenArray<>();
        map.forEach((k,v)->ret.add(v));
        return ret;

    }
}