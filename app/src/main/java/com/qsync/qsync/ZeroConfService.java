package com.qsync.qsync;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ZeroConfService {

    private static final String TAG = "ZeroConfService";
    private static final String SERVICE_TYPE = "_qsync._tcp.";

    private final Context context;
    private final NsdManager nsdManager;
    private final Handler handler;
    private final AccesBdd acces;
    public ZeroConfService(Context context) {
        this.context = context;
        this.nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        this.handler = new Handler(Looper.getMainLooper());
        this.acces = new AccesBdd(context); // Assuming AccessBdd is the equivalent of bdd.AccesBdd in Java
        acces.InitConnection();
    }

    public void browse() {

        // Get online devices from the database
        Globals.GenArray<String> oldConnectedDevices = acces.getOnlineDevices();

        // Get devices discovered on the network
        Globals.GenArray<Map<String, String>> newConnectedDevices = getNetworkDevices();

        // Update device connection states
        for(int i=0;i<oldConnectedDevices.size();i++){
            acces.SetDevicedbState(oldConnectedDevices.get(i), false);
        }

        for(int i=0;i<newConnectedDevices.size();i++) {

            Map<String, String> newDevice = newConnectedDevices.get(i);

            String deviceId = newDevice.get("device_id");
            if (acces.IsDeviceLinked(deviceId)) {
                Log.d(TAG, "Detected device: " + newDevice);
                acces.SetDevicedbState(deviceId, true, newDevice.get("ip_addr"));
                Log.d(TAG, "Checking if it missed any updates:");

                if (acces.needsUpdate(deviceId)) {
                    // Get the event queues for the device
                    Map<String, Globals.GenArray<Globals.QEvent>> multiQueue = acces.BuildEventQueueFromRetard(deviceId);

                    // Process each event queue
                    for (Map.Entry<String, Globals.GenArray<Globals.QEvent>> entry : multiQueue.entrySet()) {
                        String secureId = entry.getKey();
                        Globals.GenArray<Globals.QEvent> ptrQueue = entry.getValue();

                        // Convert the list of pointers to actual values
                        Globals.GenArray<Globals.QEvent> queue = new Globals.GenArray<>();
                        for (int j=0;j<multiQueue.size();j++) {
                            queue.add(ptrQueue.get(i));
                        }

                        // Send event queue over the network
                        Globals.GenArray<String> deviceIds = new Globals.GenArray<>();
                        deviceIds.add(deviceId);
                        Networking.sendDeviceEventQueueOverNetwork(deviceIds, secureId, queue, newDevice.get("ip_addr"));
                    }

                    // Remove device from the update queue
                    acces.removeDeviceFromRetard(deviceId);
                }
            }
        }

        acces.closedb();
    }



    public void register() {
        String serviceName = "_qsync";
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName(serviceName);
        serviceInfo.setServiceType(SERVICE_TYPE);
        serviceInfo.setPort(8274);

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener);
    }

    public void shutdown() {
        nsdManager.unregisterService(registrationListener);
    }

    public Globals.GenArray<Map<String, String>> getNetworkDevices() {
        final Globals.GenArray<Map<String, String>> devicesList = new Globals.GenArray<>();

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
                Log.d(TAG, "Service found: " + serviceInfo);
                Map<String, String> device = new HashMap<>();
                device.put("host", serviceInfo.getServiceName());
                device.put("ip_addr", serviceInfo.getHost().getHostAddress());
                device.put("port", String.valueOf(serviceInfo.getPort()));
                device.put("version", serviceInfo.getAttributes().get("version").toString());
                device.put("device_id", serviceInfo.getAttributes().get("device_id").toString());
                devicesList.add(device);
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "Service lost: " + serviceInfo);
            }
        };

        nsdManager.discoverServices("_qsync._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListener);

        return devicesList;
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

    private final Runnable updateLoopRunnable = new Runnable() {
        @Override
        public void run() {
            browse();
            handler.postDelayed(this, 10 * 1000); // Run every 10 seconds
        }
    };

}
