/*
 * *
 *  * Created by Théo Mougnibas on 27/06/2024 17:18
 *  * Copyright (c) 2024 . All rights reserved.
 *  * Last modified 27/06/2024 17:18
 *
 */

package com.ecosys.ecosys;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ZeroConfService {

    private static final String TAG = "Ecosys Server : ZeroConfService";
    private static final String SERVICE_TYPE = "_ecosys._tcp.";

    private final Context context;
    private final NsdManager nsdManager;
    private final Handler handler;
    private final AccesBdd acces;
    private static Globals.GenArray<Map<String, String>> connected_devices = new Globals.GenArray<>();
    private static final int PORT = 8274;

    public ZeroConfService(Context context) {
        this.context = context;
        this.nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        this.handler = new Handler(Looper.getMainLooper());
        this.acces = new AccesBdd(context);
        register();
        startmDnsListener();
    }





    private String getAndroidId() {
        // Retrieve the Android ID
        String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        return androidId;
    }

    public void register() {
        String serviceName = getAndroidId();
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName(serviceName);
        serviceInfo.setServiceType(SERVICE_TYPE);
        String device_id = acces.getMyDeviceId();
        serviceInfo.setAttribute("device_id",device_id);
        serviceInfo.setAttribute("version","0.0.1-PreAlpha");


        serviceInfo.setPort(PORT);

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener);
        Log.d(TAG,"Device Ecosys service registered."+serviceInfo.toString());


        // put all the devices as disconnected to start fresh
        Globals.GenArray<String> linkedDevices = acces.getLinkedDevices();
        for(int i =0;i<linkedDevices.size();i++){

                acces.setDevicedbState(
                        linkedDevices.get(i),
                        false,
                        ""
                );

        }
    }

    public void shutdown() {
        nsdManager.unregisterService(registrationListener);
    }



    private final NsdManager.RegistrationListener registrationListener = new NsdManager.RegistrationListener() {
        @Override
        public void onServiceRegistered(NsdServiceInfo serviceInfo) {
            Log.d(TAG, "Service registered: " + serviceInfo);
        }

        @Override
        public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
            Log.e(TAG, "Registration failed: " + errorCode);
        }

        @Override
        public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
            Log.d(TAG, "Service unregistered: " + serviceInfo);
        }

        @Override
        public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
            Log.e(TAG, "Unregistration failed: " + errorCode);
        }
    };




    public void startmDnsListener() {

        NsdManager nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        NsdManager.DiscoveryListener discoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery failed: Error code: " + errorCode);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery stopped: Error code: " + errorCode);
            }

            @Override
            public void onDiscoveryStarted(String serviceType) {
                Log.d(TAG, "Service discovery started");
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.d(TAG, "Service discovery stopped");
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {

                if(serviceInfo.getServiceType().equals(SERVICE_TYPE)){
                    nsdManager.resolveService(serviceInfo,initializeResolveListener());
                    Log.d(TAG, "Ecosys friend service found :D : " + serviceInfo);

                }


            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "Service lost: " + serviceInfo);

                // find a way to devices.get(i).get("hostname")check the service type.


                // checking if the lost service is a ecosys device and setting it to offline
                for(int i =0;i<connected_devices.size();i++){
                    if(!Networking.CheckIfDeviceOnline(connected_devices.get(i).get("ip_addr"),PORT)){
                        if(acces.isDeviceLinked(connected_devices.get(i).get("device_id"))){
                            acces.setDevicedbState(
                                    connected_devices.get(i).get("device_id"),
                                    false,
                                    connected_devices.get(i).get("ip_addr")
                            );
                        }

                        acces.removeDeviceFromNetworkMap(connected_devices.get(i).get("device_id"),connected_devices.get(i).get("ip_addr"));
                        connected_devices.del(i);
                        break;
                    }

                }


            }
        };

        nsdManager.discoverServices("_ecosys._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListener);

    }

    public NsdManager.ResolveListener initializeResolveListener() {
       return new NsdManager.ResolveListener() {

            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                // Called when the resolve fails. Use the error code to debug.
                Log.e(TAG, "Resolve failed: " + errorCode);
            }

            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                Log.e(TAG, "Resolve Succeeded. " + serviceInfo);

                Map<String, String> device = new HashMap<>();
                device.put("host", serviceInfo.getServiceName());
                device.put("ip_addr", serviceInfo.getHost().getHostAddress());
                device.put("port", String.valueOf(serviceInfo.getPort()));
                device.put("version", new String(serviceInfo.getAttributes().get("version"), StandardCharsets.UTF_8));
                device.put("device_id", new String(serviceInfo.getAttributes().get("device_id"), StandardCharsets.UTF_8));
                Log.d(TAG, "Detected device: " + device);


                // filter duplicates
                if(!acces.isDeviceOnNetworkMap(device.get("ip_addr"))){
                    connected_devices.add(device);


                    acces.addDeviceInNetworkMap(device.get("device_id"),device.get("ip_addr"),device.get("host"));



                    if (acces.isDeviceLinked(device.get("device_id"))) {
                        acces.setDevicedbState(device.get("device_id"), true, device.get("ip_addr"));
                        Log.d(TAG, "Checking if it missed any updates:");


                        if (acces.needsUpdate(device.get("device_id"))) {

                            BackendApi.showLoadingNotification(context,"Detected a linked device, sending him updates...");

                            // Get the event queues for the device
                            Map<String, Globals.GenArray<Globals.QEvent>> multiQueue = acces.buildEventQueueFromRetard(device.get("device_id"));

                            // Process each event queue
                            for (Map.Entry<String, Globals.GenArray<Globals.QEvent>> entry : multiQueue.entrySet()) {
                                String secureId = entry.getKey();
                                Globals.GenArray<Globals.QEvent> ptrQueue = entry.getValue();

                                // Convert the list of pointers to actual values
                                Globals.GenArray<Globals.QEvent> queue = new Globals.GenArray<>();
                                for (int j=0;j<multiQueue.size();j++) {
                                    queue.add(ptrQueue.get(j));
                                }

                                // Send event queue over the network
                                Globals.GenArray<String> deviceIds = new Globals.GenArray<>();
                                deviceIds.add(device.get("device_id"));
                                Networking.sendDeviceEventQueueOverNetwork(deviceIds, secureId, queue, device.get("ip_addr"));
                            }

                            // Remove device from the update queue
                            // NOT USED ANYMORE AS WE NOW WAIT TO HAVE CONFIRMATION EVENT TO REMOVE FROM DB
                            // SO WE ARE NETWORK ERRORS PROOF :)
                            //acces.removeDeviceFromRetard(device.get("device_id"));

                            BackendApi.discardLoadingNotification(context);
                        }
                    }
                }


            }
        };


    }




}
