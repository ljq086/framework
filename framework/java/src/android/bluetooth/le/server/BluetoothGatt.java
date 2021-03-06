/*
 * Copyright (c) 2012 Naranjo Manuel Francisco
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.bluetooth.le.server;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.Vector;

import org.freedesktop.dbus.Path;
import org.freedesktop.dbus.Variant;

import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.server.GattToolWrapper.SEC_LEVEL;
import android.bluetooth.le.server.GattToolWrapper.SHELL_ERRORS;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IIntentReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.util.Log;

import com.broadcom.bt.le.api.BleAdapter;
import com.broadcom.bt.le.api.BleConstants;
import com.broadcom.bt.le.api.BleGattID;
import com.broadcom.bt.le.api.IBleCharacteristicDataCallback;
import com.broadcom.bt.le.api.IBleClientCallback;
import com.broadcom.bt.le.api.IBleProfileEventCallback;
import com.broadcom.bt.le.api.IBleServiceCallback;
import com.broadcom.bt.service.gatt.BluetoothGattCharDescrID;
import com.broadcom.bt.service.gatt.BluetoothGattCharID;
import com.broadcom.bt.service.gatt.BluetoothGattID;
import com.broadcom.bt.service.gatt.BluetoothGattInclSrvcID;
import com.broadcom.bt.service.gatt.IBluetoothGatt;

public class BluetoothGatt extends IBluetoothGatt.Stub implements
        BlueZInterface.Listener, GattToolWrapper.GattToolListener {
    private BlueZInterface mBluezInterface;
    private BluetoothAdapter mAdapter;
    private IActivityManager mAm;
    private static String TAG = "BT-GATT";

    public static final String BLUETOOTH_PERM = "android.permission.BLUETOOTH";
    public static final String BLUETOOTH_LE_SERVICE = BleConstants.BLUETOOTH_LE_SERVICE;

    public static int API_LEVEL = 5;
    public static String FRAMEWORK_VERSION = "0.5.8";

    private Map<BluetoothGattID, AppWrapper> registeredApps = new HashMap<BluetoothGattID, AppWrapper>();
    private AppWrapper[] registeredAppsByID = new AppWrapper[Byte.MAX_VALUE];
    private byte mNextAppID = 0;
    
    private Set<String> mLeDevices = new HashSet<String>();
    
    private String toIntHexString(int t){
        return IntegralToString.intToHexString(t, false, 0);
    }

    /**
     * this class member is used for enabling and disabling the Bluez interface
     * depending on whether it's running or not.
     */
    BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (i.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                int s = i.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);
                if (s == BluetoothAdapter.STATE_ON) {
                    Log.v(TAG, "enabling my interface");
                    Thread a = new Thread() {
                        public void run() {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                Log.e(TAG, "error", e);
                            }
                            BluetoothGatt.this.mBluezInterface.Start(true);
                        }
                    };
                    a.start();

                } else if (s == BluetoothAdapter.STATE_OFF) {
                    Log.v(TAG, "bluez is down");
                    BluetoothGatt.this.mBluezInterface.Stop();
                }
            }

        }
    };

    /**
     * Constructor for the class, initializes the pieces needed by us.
     * 
     * @throws IOException if GattTool pool fails to initialize.
     */
    public BluetoothGatt() throws IOException {
        Log.v(TAG, "new bluetoothGatt");

        mAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mAdapter == null)
            throw new RuntimeException("Bluetooth Adapter not ready");

        mAm = ActivityManagerNative.getDefault();
        if (mAm == null)
            throw new RuntimeException("Activity Manager not ready");

        this.initBroadcast();

        mBluezInterface = new BlueZInterface(this);
        mBluezInterface.Start();

        registerBroadcastReceiver(mReceiver,
                new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
    }

    @SuppressWarnings("rawtypes")
    @Override
    /**
     * This function will return the kind of Bluetooth Device based on the 
     * Bluetooth Address.
     */
    public byte getDeviceType(String address) {
        if (mLeDevices.contains(address))
            return BleConstants.GATT_TRANSPORT_LE;
        
        Map<String, Variant> prop = null;
        try {
            prop = mBluezInterface.getDeviceProperties(address);
        } catch (Exception e) {
            Log.e(TAG, "error on getDeviceType", e);
        }
        if (prop == null)
            return BleConstants.GATT_UNDEFINED;

        /*
         * LE provides - Address, RSSI, Name, Paired, Broadcaster, UUIDs, Class
         * 0 or no class at all BD/EDR provides: Address, Class, Icon, RSSI,
         * Name, Alias, LegacyPairing, Paired, UUIDs
         */
        if (prop.containsKey("Icon"))
            return BleAdapter.DEVICE_TYPE_BREDR;
        
        if (prop.containsKey("Broadcaster") || !prop.containsKey("Class")){
            mLeDevices.add(address);
            return BleAdapter.DEVICE_TYPE_BLE;
        }
        
        @SuppressWarnings("unchecked")
        List<Path> t = (List<Path>) prop.get("Services").getValue();
        if (t!=null && t.size()>0) {
            mLeDevices.add(address);
            return BleAdapter.DEVICE_TYPE_BLE;
        }

        if (prop.get("Class").equals(new Integer(0))){
            mLeDevices.add(address);
            return BleAdapter.DEVICE_TYPE_BLE;
        }
        
        return BleAdapter.DEVICE_TYPE_BREDR;
    }

    @SuppressWarnings("unused")
    /**
     *
     */
    private class IntentReceiver extends IIntentReceiver.Stub {
        private boolean mFinished = false;

        public synchronized void performReceive(
                Intent intent, int rc, String data, Bundle ext, boolean ord,
                boolean sticky) {
            String line = "Broadcast completed: result=" + rc;
            if (data != null)
                line = line + ", data=\"" + data + "\"";
            if (ext != null)
                line = line + ", extras: " + ext;
            Log.v(TAG, line);
            mFinished = true;
            notifyAll();
        }

        public synchronized void waitForFinish() {
            try {
                while (!mFinished)
                    wait();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private class IBroadcastReceiver extends IIntentReceiver.Stub {
        private BroadcastReceiver receiver = null;

        public IBroadcastReceiver(BroadcastReceiver receiver) {
            super();
            this.receiver = receiver;
        }

        public synchronized void performReceive(
                Intent intent, int rc, String data, Bundle ext, boolean ord,
                boolean sticky) {
            String line = "Broadcast received: " + intent;
            Log.v(TAG, line);
            receiver.onReceive(null, intent);
            notifyAll();
        }
    }

    private Method mBroadcast;
    private Class<?>[] mBroadcastArgs;

    private void initBroadcast() {
        if (mBroadcast != null)
            return;
        Class<?> c;
        try {
            c = Class.forName("android.app.IActivityManager");
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "failed on initBroadcast", e);
            return;
        }
        Log.i(TAG, "found class");

        for (Method m : c.getMethods()) {
            Log.v(TAG, "m " + m.getName());

            if (m.getName().equals("broadcastIntent")) {
                mBroadcast = m;
                mBroadcastArgs = m.getParameterTypes();
                Log.v(TAG, "found method, argument count " + mBroadcastArgs.length);

                for (int i = 0; i < mBroadcastArgs.length; i++) {
                    Log.v(TAG, "argument " + i + " " + mBroadcastArgs[i]);
                }
                return;
            }
        }

        Log.e(TAG, "couldn't resolve broadcastIntent");
    }

    /**
     * Internal method that will broadcast intents using reflections.
     * 
     * @param intent
     */
    public void broadcastIntent(Intent intent) {
        if (mBroadcast == null) {
            Log.v(TAG, "no broadcastIntent, sorry");
            return;
        }

        Object[] args = new Object[mBroadcastArgs.length];

        boolean flag = true;

        for (int i = 0; i < args.length; i++) {
            if (mBroadcastArgs[i].equals(Intent.class)) {
                args[i] = intent;
                continue;
            }

            if (mBroadcastArgs[i].equals(int.class)) {
                args[i] = 0;
                continue;
            }

            if (mBroadcastArgs[i].equals(boolean.class)) {
                args[i] = flag;
                if (flag)
                    flag = false;
                continue;
            }

            args[i] = null;
        }

        try {
            Log.v(TAG, "broadcasting " + args);
            mBroadcast.invoke(mAm, args);
        } catch (Exception e) {
            Log.e(TAG, "failed to broadcast signal!", e);
        }
    }

    private void registerBroadcastReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        Log.v(TAG, "registering broadcast receiver");
        IBroadcastReceiver ireceiver = new IBroadcastReceiver(receiver);
        try {
            mAm.registerReceiver(null, null, ireceiver, filter, null);
            Log.v(TAG, "registered");
            return;
        } catch (RemoteException e) {
            Log.e(TAG, "failed registering receiver");
        }
    }

    @Override
    /**
     * Will get called by BlueZ layer when a new device is discovered.
     */
    public void deviceDiscovered(String address, String name, short rssi) {
        Intent intent = new Intent(BluetoothDevice.ACTION_FOUND);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mAdapter.getRemoteDevice(address));
        intent.putExtra(BluetoothDevice.EXTRA_NAME, name);
        intent.putExtra(BluetoothDevice.EXTRA_RSSI, rssi);
        intent.putExtra(BleAdapter.EXTRA_DEVICE_TYPE, BleAdapter.DEVICE_TYPE_BLE);
        broadcastIntent(intent);
        mLeDevices.add(address);
    }

    @Override
    /**
     * Allows clients to list all the UUIDs provided by the remote device
     * address.
     */
    public void getUUIDs(String address) {
        List<String> uuids = null;

        try {
            uuids = mBluezInterface.getUUIDs(address);
        } catch (Exception e) {
            Log.e(TAG, "failed to get uuids for " + address, e);
            return;
        }

        BluetoothDevice d = mAdapter.getRemoteDevice(address);
        for (String u : uuids) {
            Intent intent = new Intent(BleAdapter.ACTION_UUID);
            intent.putExtra(BleAdapter.EXTRA_DEVICE, d);
            intent.putExtra(BleAdapter.EXTRA_UUID, new ParcelUuid(UUID.fromString(u)));
            broadcastIntent(intent);
        }
        Intent intent = new Intent(BleAdapter.ACTION_UUID);
        intent.putExtra(BleAdapter.EXTRA_DEVICE, d);

        broadcastIntent(intent);
    }
    
    /**
     * Internal class used to wrap all the information needed to talk to binder
     * clients.
     */
    private Set<AppWrapper> knownApps = new HashSet<AppWrapper>();
    
    class AppWrapper {
        BluetoothGattID mGattID;
        byte mIfaceID;
        IBleClientCallback mCallback;
        DeathRecipient deadRecipient;

        public AppWrapper(BluetoothGattID mGattID, byte mIfaceID, IBleClientCallback mCallback) {
            super();
            this.mGattID = mGattID;
            this.mIfaceID = mIfaceID;
            this.mCallback = mCallback;
            knownApps.add(this);
        }
        
        private synchronized void cleanupApp(){
            Log.v(TAG, "cleaning up application " + mIfaceID);
            for (Map.Entry<String, ConnectionWrapper> cm: mPendingConnections.entrySet()){
                ConnectionWrapper cw = cm.getValue();
                if (cw.wrapper.mIfaceID != mIfaceID)
                    continue;
                Log.v(TAG, "dropping pending connection from this client " + cm.getKey());
                cw.mGattTool.setListener(null);
                cw.mGattTool.disconnect();
                cw.mGattTool.releaseWorker();
                mPendingConnections.remove(cm.getKey());
            }
            for (Map.Entry<Integer, ConnectionWrapper> cm: mConnectionMap.entrySet()){
                ConnectionWrapper cw = cm.getValue();
                if (cw.wrapper.mIfaceID != mIfaceID)
                    continue;
                Log.v(TAG, "dropping open client from this client " + cw.remote);
                cw.mGattTool.setListener(null);
                cw.mGattTool.disconnect();
                cw.mGattTool.releaseWorker();
                for (Service s: cw.mServiceByHandle.values()){
                    for (Characteristic c: s.chars){
                        c.descriptors.clear();
                        c.service = null;
                    }
                    s.chars.clear();
                    s.callback = null;
                }
                cw.mServiceByHandle.clear();
                cw.mAttributesByHandle.clear();
                cw.mCharacteristicByHandle.clear();
                mConnectionMap.remove(cm.getKey());
            }
            if (deadRecipient!=null) {
                boolean r = mCallback.asBinder().
                        unlinkToDeath(deadRecipient, 0);
                if (!r)
                    Log.e(TAG, "unlinkToDeath failed");
            }
            deadRecipient = null;
            knownApps.remove(this);
            registeredAppsByID[this.mIfaceID] = null;
            Log.v(TAG, "AppWrapper for " + this.mIfaceID + " cleaned");
        }
    }
    

    /**
     * Internal class that allows to map connection ids with remote address,
     * application wrapper and gatttool instance.
     */
    private class ConnectionWrapper {
        int connID;
        boolean deviceBR;
        AppWrapper wrapper;
        String remote;
        GattToolWrapper mGattTool;
        Map<BleGattID, List<Service>> services;
        BleGattID lastPrimaryUuid;
        Service lastService;
        boolean sec_result;
        Map<Integer, Service> mServiceByHandle = new HashMap<Integer, Service>();
        Map<Integer, Characteristic> mCharacteristicByHandle = new HashMap<Integer, Characteristic>();
        Map<Integer, Attribute> mAttributesByHandle = new HashMap<Integer, Attribute>();
        
        public ConnectionWrapper(AppWrapper w, String r) {
            this.connID = -1; // mark as pending
            this.wrapper = w;
            this.remote = r;
            this.services = new HashMap<BleGattID, List<Service>>();
            this.lastPrimaryUuid = null;
            this.deviceBR = false;
        }
        
        public Service addService(int start, int end, BleGattID uuid){
            if (!services.containsKey(uuid))
                services.put(uuid, new Vector<Service>());
            
            int sid = services.get(uuid).size();
            int u16 = uuid.getUuid16();
            BluetoothGattID svcId;
            if (u16 > -1)
                svcId = new BluetoothGattID(sid, u16);
            else
                svcId = new BluetoothGattID(sid, uuid.getUuid());
            Service s = new Service(svcId, start, end);
            services.get(uuid).add(s);
            s.conn = this;
            this.mServiceByHandle.put(start, s);
            return s;
        }
    }

    /**
     * internal class used for "remembering the services", only once instance of this
     * class should be available per registered service per instance per device.
     */
    private class Service {
        BluetoothGattID uuid;
        int start;
        int end = 0xffff;
        List<Characteristic> chars = new Vector<Characteristic>();
        Characteristic lastChar = null;
        Integer lastCharResult = null;
        ConnectionWrapper conn;
       
        IBleCharacteristicDataCallback callback;
        
        public Service(BluetoothGattID u, int s, int e){
            Log.v(TAG, "new Service " + u + " start: " + s + " end: " + e);
            this.uuid = u;
            this.start = s;
            this.end = e;
        }
        
        public void addCharacteristic(Characteristic c){
            c.service = this;
            c.uuid.setInstId(this.chars.size());
            this.chars.add(c);
            if (chars.size()>1)
                chars.get(c.uuid.getInstanceID()-1).end = c.handle-1;
            c.service.conn.mCharacteristicByHandle.put(c.handle, c);
        }
    }
    
    private class Attribute {
        int handle;
        BleGattID uuid;
        Service service;
        
        public Attribute(int handle, BleGattID uuid){
            this.handle = handle;
            this.uuid = uuid;
        }
    }
    
    @SuppressWarnings("unused")
    private class Characteristic extends Attribute{
        short properties;
        int value_handle;
        int end = 0xffff;
        boolean descFlag;
        Descriptor lastDescriptor;
        Integer lastDescriptorStatus = null;
        List<Descriptor> descriptors = new Vector<Descriptor>();
        
        public Characteristic(int handle, short properties, int value_handle, BleGattID id){
            super (handle, id);
            this.properties = properties;
            this.value_handle = value_handle;
        }
        
        public void addDescriptor(Descriptor d){
            d.uuid.setInstanceId(this.descriptors.size());
            this.descriptors.add(d);
            d.parent = this;
            service.conn.mAttributesByHandle.put(d.handle, d);
            d.service = service;
        }
        
        public void addValueAttribute(Attribute a){
            service.conn.mAttributesByHandle.put(a.handle, a);
            a.service = service;
        }
    }
    
    @SuppressWarnings("unused")
    private class Descriptor extends Attribute{
        Characteristic parent;
        
        public Descriptor(int handle, BleGattID uuid){
            super(handle, uuid);
        }
    }

    /*
     * Map of connections that still didn't complete or failed, we use Address
     * as key as we don't have a connection handle until connection is
     * stablished.
     */
    private Map<String, ConnectionWrapper> mPendingConnections =
            new HashMap<String, ConnectionWrapper>();

    /*
     * Map of connections running, we map with connection id as we now it.
     */
    private Map<Integer, ConnectionWrapper> mConnectionMap =
            new HashMap<Integer, ConnectionWrapper>();

    /* ************************************************************************************
     * Connection handling methods
     * ***********************************************
     * ************************************
     */
    @Override
    /**
     * Callback from GattToolWrapper when connection completes or fails.
     */
    public void connected(GattToolWrapper w, int connID, String addr, int status) {
        Log.v(TAG, "connected " + addr + " -> " + connID + " " + status);
        if (!mPendingConnections.containsKey(addr)) {
            Log.e(TAG, "remote no longer pending!");
            w.notifyAll();
            return;
        }

        ConnectionWrapper cw = mPendingConnections.get(addr);
        
        if (cw == null){
            w.notifyAll();
            return;
        }
        
        mPendingConnections.remove(addr);

        cw.connID = connID;

        try {
            if (status == BleConstants.GATT_SUCCESS) {
                mConnectionMap.put(connID, cw);
                w.notifyAll();
                cw.wrapper.mCallback.onConnected(addr, connID);
            }
            else {
                w.notifyAll();
                cw.wrapper.mCallback.onDisconnected(connID, addr);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "failed calling callback from connection wrapper", e);
        }

        Log.v(TAG, "connected end");
    }

    @Override
    /**
     * Method called by binder clients to start connecting to remote devices.
     */
    public synchronized void open(byte interfaceID, final String remote, boolean foreground) {
        Log.v(TAG, "open " + interfaceID + " " + remote);

        final AppWrapper w = this.registeredAppsByID[interfaceID];

        GattToolWrapper gtw = null;
        ConnectionWrapper cw = null;

        try {
            gtw = GattToolWrapper.getWorker();
            cw = new ConnectionWrapper(w, remote);
            gtw.setListener(this); // register to get signals from this worker.
            cw.mGattTool = gtw;
            mPendingConnections.put(remote, cw);
        } catch (Exception e) {
            Log.e(TAG, "something failed while getting gatttool wrapper and connection wrapper", e);
            try {
                w.mCallback.onConnected(remote, -1);
            } catch (RemoteException e2) {
                Log.e(TAG, "we failed to notify other end", e2);
            }
            return;
        }

        if (getDeviceType(remote) == BleAdapter.DEVICE_TYPE_BREDR) {
            Log.v(TAG, "Connecting to BR device, setting psm=31");
            synchronized (gtw){
                gtw.psm(31);
                cw.deviceBR = true;
                try {
                    gtw.wait();
                } catch (InterruptedException e) {
                    Log.e(TAG, "interrupt while setting psm", e);
                }	
            }
        }
        synchronized (gtw) {
            gtw.connect(remote);

            if (foreground)
                try {
                    cw.mGattTool.wait();
                } catch (InterruptedException e) {
                }
        }
        Log.v(TAG, "open end");
    }

    @Override
    /**
     * Callback from GattToolWrapper that tells us when a connection has
     * been closed, for what ever reason it did.
     */
    public void disconnected(GattToolWrapper w, int connID, String addr) {
        Log.v(TAG, "disconnected " + addr + " -> " + connID);
        ConnectionWrapper cw;
        if (mPendingConnections.containsKey(addr)) {
            Log.i(TAG, "disconnect on pending connection");
            cw = mPendingConnections.get(addr);
            mPendingConnections.remove(addr);
        } else if (mConnectionMap.containsKey(connID)) {
            Log.i(TAG, "disconnected from real connection");
            cw = mConnectionMap.get(addr);
            mConnectionMap.remove(addr);
        } else {
            Log.e(TAG, "Address is not registered as pending or connected, aborting");
            w.releaseWorker();
            w.notifyAll();
            return;
        }
        w.releaseWorker();
        w.notifyAll();
        
        try {
            cw.wrapper.mCallback.onDisconnected(connID, addr);
        } catch (RemoteException e) {
            Log.e(TAG, "failed calling callback from connection wrapper", e);
        }
    }

    @Override
    /**
     * Method that binder clients will call when they want to close a connection, or
     * cancel a pending connection. The way to tell what's the case is given by
     * connHandle, connHandle should be 0 for pending connections.
     */
    public void close(final byte interfaceID, final String remote,
            int connHandle, boolean foreground) {
        Log.v(TAG, "close called for " + remote + " ifaceID " + interfaceID
                + " connHandle " + connHandle);

        ConnectionWrapper cw = null;
        if (mPendingConnections.containsKey(remote)) {
            cw = mPendingConnections.get(remote);
            mPendingConnections.remove(remote);
        } else if (mConnectionMap.containsKey(connHandle)) {
            cw = mConnectionMap.get(connHandle);
            mConnectionMap.remove(connHandle);
        } else  {
            Log.e(TAG, "disconnect for non pending or known connection");
            return;
        }

        synchronized (cw.mGattTool) {
            cw.mGattTool.releaseWorker();
            try {
                Log.v(TAG, "about to notify closing completed");
                cw.wrapper.mCallback.onDisconnected(connHandle, remote);
            } catch (RemoteException e) {
                Log.e(TAG, "failed notifiying we closed the connection");
            }
        }
        cw.mGattTool = null;   
        Log.v(TAG, "close done");
    }

    /* *******************************************************************************
     * Application handling methods
     * ********************************
     */
    private boolean isApplicationAlive(AppWrapper wrapper){
        return wrapper.mCallback.asBinder().pingBinder();
    }
    
    @Override
    /**
     * This method will get called when ever a new BLE application starts running.
     * Apps needs to be registered for us to talk to it.
     */
    public synchronized void registerApp(BluetoothGattID appUuid, IBleClientCallback callback) {
        AppWrapper wrapper = null;
        Log.v(TAG, "register app " + appUuid + " callback " + callback);
        if (registeredApps.containsKey(appUuid)) {
            Log.v(TAG, "uuid all ready registered");

            wrapper = registeredApps.get(appUuid);
            if (isApplicationAlive(wrapper)) {
                Log.e(TAG, "uuid is registered and alive");
                try {
                    callback.onAppRegistered((byte) BleConstants.GATT_ERROR, (byte) -1);
                } catch (RemoteException e) {
                    Log.e(TAG, "failed to tell other end", e);
                }
                return;
            }
            Log.v(TAG, "no ping back " + appUuid + " registering again");
            wrapper.cleanupApp();
        }
        int status = BleConstants.GATT_SUCCESS;

        if (wrapper == null) {
            if (mNextAppID == Byte.MAX_VALUE)
                status = BleConstants.GATT_ERROR;
            else
                wrapper = new AppWrapper(appUuid, ++mNextAppID, callback);
        } else
            wrapper.mCallback = callback;

        byte ifaceID = -1;

        if (wrapper != null) {
            this.registeredAppsByID[wrapper.mIfaceID] = wrapper;
            registeredApps.put(appUuid, wrapper);
            ifaceID = wrapper.mIfaceID;
            final AppWrapper w = wrapper;
            DeathRecipient dr = new DeathRecipient() {
                public void binderDied() {
                    Log.e(TAG, "application died without calling unregisterApp first " + w.mIfaceID);
                    w.cleanupApp();
                }
            };
            w.deadRecipient = dr;
            try {
                callback.asBinder().linkToDeath(dr, 0);
            } catch (RemoteException e) {
                Log.e(TAG, "failed to do linkToDeath");
            }
        }
        


        try {
            callback.onAppRegistered((byte) status, ifaceID);
        } catch (RemoteException e) {
            Log.e(TAG, "Faield to notify AppRegistered", e);
        }
    }

    @Override
    /**
     * When an application is finishing or don't want to do LE any more should
     * call this method.
     */
    public synchronized void unregisterApp(byte interfaceID) {
        for (Entry<BluetoothGattID, AppWrapper> v : registeredApps.entrySet()) {
            AppWrapper a = v.getValue();
            if (a.mIfaceID != interfaceID)
                continue;

            a.cleanupApp();
            try {
                a.mCallback.onAppDeregistered(interfaceID);
            } catch (RemoteException e) {
                Log.e(TAG, "failed notifying client of deregistration", e);
            }
            Log.v(TAG, "app successfully unregistered for interface: " + interfaceID +
                    ", uuid: " + v.getValue().mGattID);
            return;
        }
        Log.e(TAG, "interfaceID not known " + interfaceID);
        return;
    }

    private ConnectionWrapper getConnectionWrapperForAddress(String remote) {
        for (Entry<String, ConnectionWrapper> e : mPendingConnections.entrySet()) {
            ConnectionWrapper cw = e.getValue();
            if (cw.remote.equals(remote))
                return cw;
        }

        for (Entry<Integer, ConnectionWrapper> e : mConnectionMap.entrySet()) {
            ConnectionWrapper cw = e.getValue();
            if (cw.remote.equals(remote))
                return cw;
        }
        
        return null;
    }
    
    @Override
    /**
     * callback from GattToolWrapper to let us know sec-level transaction completed
     */
    public void gotSecurityLevelResult(GattToolWrapper w, int connID, int status) {
        Log.v(TAG, "got security level result " + connID + " " + status);
        
        ConnectionWrapper cw = getConnectionWrapperForConnID(connID,"gotSecurityLevelResult");
        if (cw==null){
            Log.e(TAG, "no connection wrapper for this address");
            w.notifyAll();
            return;
        }
        
        cw.sec_result = status == BleConstants.GATT_SUCCESS;
        w.notifyAll();
    }

    @Override
    /**
     * Starts the bonding process with a remote device
     */
    public boolean createBond(String address) {
        Log.v(TAG, "createBond for " + address);
        
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            Log.e(TAG, "invalid address");
            return false;
        }
        BluetoothDevice dev = this.mAdapter.getRemoteDevice(address);
        if (dev.getBondState() != BluetoothDevice.BOND_NONE) {
            Log.e(TAG, "bond state is not bond_none");
            return false;
        }
        
        Log.i(TAG, "starting bond process");
        return dev.createBond();
    }
    
    @Override
    /**
     * Cancels the bonding process with a remote device
     */
    public boolean cancelBond(String address) {
        Log.v(TAG, "createBond for " + address);
        
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            Log.e(TAG, "invalid address");
            return false;
        }
        BluetoothDevice dev = this.mAdapter.getRemoteDevice(address);
        Log.i(TAG, "canceling bond process");
        return dev.cancelBondProcess();	
    }
    
    @Override
    /**
     * Change the security level of the connection.
     */
    public synchronized boolean setEncryption(String address, byte action) {
        Log.v(TAG, "setEncryption " + address + " " + action);

        if (action == BleConstants.GATT_ENCRYPT_NONE) {
            Log.v(TAG, "no encryption ignoring");
            return false;
        }
        
        ConnectionWrapper cw = getConnectionWrapperForAddress(address);
        if (cw==null){
            Log.e(TAG, "no connection wrapper for this address");
            return false;
        }
        
        BluetoothDevice mDevice = this.mAdapter.getRemoteDevice(address);
        if (mDevice.getBondState()!=BluetoothDevice.BOND_BONDED){
            Log.e(TAG, "need to be bonded first");
            return false;
        }
        
        if (cw.mGattTool==null){
            Log.e(TAG, "no gatttool wrapper for this address");
            return false;
        }
        
        cw.sec_result = false;

        if (action == BleConstants.GATT_ENCRYPT)
            cw.mGattTool.secLevel(SEC_LEVEL.LOW);
        if (action == BleConstants.GATT_ENCRYPT_NO_MITM)
            cw.mGattTool.secLevel(SEC_LEVEL.MEDIUM);
        if (action == BleConstants.GATT_ENCRYPT_MITM)
            cw.mGattTool.secLevel(SEC_LEVEL.HIGH);
        else {
            Log.e(TAG, "invalid sec level");
            return false;
        }
        synchronized (cw.mGattTool) {
            try {
                cw.mGattTool.wait();
            } catch (InterruptedException e) {
                Log.e(TAG, "interrupted while waiting sec-level to settle");
            }
        }
        
        return cw.sec_result;
    }
    

    @SuppressWarnings("unused")
    private ConnectionWrapper getConnectionWrapperForConnID(int connID) {
        return this.getConnectionWrapperForConnID(connID, "NN");
    }
    
    private ConnectionWrapper getConnectionWrapperForConnID(int connID, String f) {
        if (!mConnectionMap.containsKey(connID)) {
            Log.e(TAG, "connection id not known on " + f);
            return null;
        }
        
        return mConnectionMap.get(connID);
    }
    
    /* ********************************************************************************
     * service search methods
     **********************************************************************************/
    @Override
    /**
     * Method called by binder clients to start a service discovery process
     */
    public synchronized void searchService(int connID, BluetoothGattID serviceID) {
        Log.v(TAG, "searchService " + connID + " " + serviceID);
        
        ConnectionWrapper cw = getConnectionWrapperForConnID(connID, "searchService");
        if (cw == null)
            return;
        
        GattToolWrapper gatt = cw.mGattTool;
        
        if (gatt == null) {
            Log.e(TAG, "gatt tool wrapper is null!!!");
            try {
                cw.wrapper.mCallback.onSearchCompleted(connID, BleConstants.GATT_ERROR);
            } catch (RemoteException e) {
                Log.e(TAG, "failed doing onSearchCompleted with error", e);
            }
            return;
        }

        if (serviceID != null) {
            BleGattID i = null;
            int u16 = serviceID.getUuid16();
            if (u16 > 0)
                i = new BleGattID(new Integer(u16));
            else
                i = new BleGattID(serviceID.getUuid());
            cw.lastPrimaryUuid = i;
            if (cw.services.containsKey(i))
                cw.services.remove(i);
            Log.v(TAG, "searcing for uuid " + i);
            gatt.primaryDiscoveryByUUID(i);
        } else {
            Log.v(TAG, "doing a general primary service discovery");
            cw.lastPrimaryUuid = null;
            cw.services.clear();
            gatt.primaryDiscovery();
        }
        synchronized (cw.mGattTool) {
            try {
                cw.mGattTool.wait();
            } catch (InterruptedException e) {
                Log.e(TAG, "interrupted on SearchService");
            }
            Log.v(TAG, "searchService end");
        }
    }
    
    @Override
    /**
     * This method is a GattToolWrapper callback that let us know for a primary result
     */
    public void primaryAll(GattToolWrapper w, int connID, int start, int end, BleGattID uuid) {
        ConnectionWrapper cw = getConnectionWrapperForConnID(connID, "primaryAll");
        if (cw == null){
            w.notifyAll();
            return;
        }
        
        Service s = cw.addService(start, end, uuid);
        try {
            w.notifyAll();
            cw.wrapper.mCallback.onSearchResult(connID, s.uuid);
        } catch (Exception e) {
            Log.e(TAG, "exception will calling onSearchResult");
        }
    }

    @Override
    /**
     * GattToolWrapper callback to let us know the primary scan completed.
     */
    public void primaryAllEnd(GattToolWrapper w, int connID, int status) {
        
        ConnectionWrapper cw = getConnectionWrapperForConnID(connID, "primaryAllEnd");
        if (cw == null){
            w.notifyAll();
            return;
        }
        
        w.notifyAll();
        try {
            cw.wrapper.mCallback.onSearchCompleted(connID, status);
        } catch (Exception e) {
            Log.e(TAG, "exception will calling onSearchCompleted");
        }
    }

    /**
     * callback from GattToolWrapper that let us know we have a result
     * from an uuid search.
     */
    @Override
    public void primaryUuid(GattToolWrapper w, int connID, int start, int end) {
        ConnectionWrapper cw = getConnectionWrapperForConnID(connID, "primaryUuid");
        if (cw == null)
            return;
        
        BleGattID uuid = cw.lastPrimaryUuid;
        Log.v(TAG, "primaryUuid " + connID + ", " + start + ", " + end);
        this.primaryAll(w, connID, start, end, uuid);
    }

    /**
     * callback that let us know a service search with uuid set completed.
     */
    @Override
    public void primaryUuidEnd(GattToolWrapper w, int connID, int status) {
        Log.v(TAG, "primaryUuidEnd " + connID + ", " + status);
        this.primaryAllEnd(w, connID, status);
    }
    
    private Service getServiceForConnIDServiceID(int connID, BluetoothGattID serviceID, String f){
        ConnectionWrapper cw = getConnectionWrapperForConnID(connID, "getServiceForConnIDServiceID for " + f);
        if (cw == null)
            return null;
        
        for (Entry<BleGattID, List<Service>>e: cw.services.entrySet()){
            if (!e.getKey().toString().equals(serviceID.toString()))
                continue;
            if (e.getValue().size()<serviceID.getInstanceID()){
                Log.e(TAG, "instanceID is bigger than known services list: " + 
                        e.getValue().size() + ", " +serviceID.getInstanceID());
                continue;
            }
            
            return e.getValue().get(serviceID.getInstanceID());
        }
        Log.v(TAG, "couldn't find ServiceWrapper in " + f);
        return null;
    }
    
    @Override
    /**
     * This method gets called by remote gatt client for registering a callback
     * function for service data related activities.
     */
    public synchronized void registerServiceDataCallback(int connID, BluetoothGattID serviceID,
            String address, IBleCharacteristicDataCallback callback) {
        Log.v(TAG, "registerServiceDataCallback");
       
        ConnectionWrapper c = getConnectionWrapperForConnID(connID, "registerServiceDataCallback");
        if (c == null){
            Log.e(TAG, "no connection wrapper, there's nothing we can do at registerServiceDataCallback");
            return;
        }
        Service s = getServiceForConnIDServiceID(connID, serviceID, "registerServiceDataCallback");
        if (s==null){
            Log.e(TAG, "can't register callback");
            return;
        }
        s.callback = callback;
        Log.v(TAG, "registered succesffully");
    }
    
    /* ****************************************************************************
     * characteristics discovery methods
     ******************************************************************************/
    
    @Override
    /**
     * This method gets called by the binder client to start a service discovery.
     */
    public synchronized void getFirstChar(int connID, BluetoothGattID serviceID, BluetoothGattID id)
    {
        Log.v(TAG, "getFirstChar " + connID + " " + serviceID + " " + id);
        ConnectionWrapper cw = getConnectionWrapperForConnID(connID, "getFirstChar");
        Service s = getServiceForConnIDServiceID(connID, serviceID, "getFirstChar");
        if (cw==null || s==null || s.callback == null){
            Log.e(TAG, "no callback access, can't discover chars");
            return;
        }
        
        if (id!=null) {
            Log.e(TAG, "no included services support yet sorry.");
            try {
                s.callback.onGetFirstIncludedService(connID, BleConstants.GATT_ERROR, serviceID, id);
            } catch (RemoteException e) {
                Log.e(TAG, "exception while calling onGetFirstIncludedService");
            }
            return;
        }
        
        cw.lastService = s;
        s.lastCharResult = null;
        s.chars.clear();
        
        cw.mGattTool.characteristicsDiscovery(s.start, s.end);
        
        synchronized (cw.mGattTool) {
            try {
                cw.mGattTool.wait();
            } catch (InterruptedException e) {
                Log.e(TAG, "getFirstChar interrupted");
            }
        }
        
        Log.v(TAG, "getFirstChar end");
    }
    
    @Override
    /**
     * GattToolWrapper callback that gets called once per discovered characteristic.
     */
    public void characteristic(GattToolWrapper w, int connID, int handle, short properties, 
            int value_handle, BleGattID uuid) {
        Log.v(TAG, "got characteristic " + connID + " " + handle);
        
        ConnectionWrapper cw = getConnectionWrapperForConnID(connID, "characteristic callback");
        if ( cw == null || cw.lastService == null){
            return;
        }
        
        Service s = cw.lastService;
        s.addCharacteristic(new Characteristic(handle, properties, value_handle, uuid));
    }
    
    @Override
    /**
     * GattToolWrapper callback telling characteristic discovery completed
     */
    public void characteristicEnd(GattToolWrapper w, int connID, int status) {
        Log.v(TAG, "characteristicEnd " + connID + " " + status);
        
        ConnectionWrapper cw = getConnectionWrapperForConnID(connID, "characteristicEnd callback");
        if ( cw == null || cw.lastService == null) {
            w.notifyAll();
            return;
        }
        
        Service s = cw.lastService;
        s.lastCharResult = new Integer(status);
        
        try {
            if (s.chars.size() > 0) {
                Characteristic c = s.chars.get(0);
                Log.v(TAG, "doing onGetFirstCharacteristic " + connID + " " + s.uuid + " " +
                        c.uuid + " " + c.properties);
                w.notifyAll();
                s.callback.onGetFirstCharacteristic(connID, s.lastCharResult.intValue(), 
                        s.uuid, c.uuid, c.properties);
            }
            else {
                w.notifyAll();
                s.callback.onGetFirstCharacteristic(connID, BleConstants.GATT_NOT_FOUND, 
                        s.uuid, null, 0);
                Log.v(TAG, "doing onGetFirstCharacteristic " + connID + " " + s.uuid + " " +
                        null + " " + 0);
            }
            s.lastCharResult = null;
        } catch (RemoteException e) {
            Log.e(TAG, "error while calling onGetFirstCharacteristic", e);
        }
        
        Log.v(TAG, "characteristicEnd finish");
    }
    
    @Override
    public synchronized void getNextChar(int connID, BluetoothGattCharID svcChrID, BluetoothGattID id) {
        Log.v(TAG, "getNextChar " + connID + " " + svcChrID + " " + id);
        BluetoothGattID serviceID = svcChrID.getSrvcId();
        BluetoothGattID prevChar = svcChrID.getCharId();
        
        ConnectionWrapper cw = getConnectionWrapperForConnID(connID, "getFirstChar");
        Service s = getServiceForConnIDServiceID(connID, serviceID, "getFirstChar");
        
        if (cw==null || s==null || s.callback == null){
            Log.e(TAG, "no callback access, can't discover chars");
            return;
        }
        
        if (id!=null) {
            Log.e(TAG, "no included services support yet sorry.");
            try {
                s.callback.onGetFirstIncludedService(connID, BleConstants.GATT_ERROR, serviceID, id);
            } catch (RemoteException e) {
                Log.e(TAG, "exception while calling onGetFirstIncludedService");
            }
            return;
        }
        
        BleGattID gid = null;
        short prop = 0;
        if (prevChar.getInstanceID() < s.chars.size()-1){
            Characteristic c = s.chars.get(prevChar.getInstanceID()+1);
            gid = c.uuid;
            prop = c.properties;
        }
        
        int status = gid != null ? BleConstants.GATT_SUCCESS : BleConstants.GATT_ERROR;

        try {
            s.callback.onGetNextCharacteristic(connID, status, serviceID, gid, prop);
        } catch (RemoteException e) {
            Log.e(TAG, "failed calling onGetNextCharacteristic with status: " + status +
                    " charID: " + gid);
        }
    }
    
    /* **************************************************************************************
     * char discovery methods
     */
    
    @Override
    public synchronized void getFirstCharDescr(int connID, BluetoothGattCharID svcChrID, BluetoothGattID id) {
        Log.v(TAG, "getFirstCharDescr " + connID + " " + svcChrID + " " + id);
        BluetoothGattID serviceID = svcChrID.getSrvcId();
        BluetoothGattID charID = svcChrID.getCharId();
        
        ConnectionWrapper cw = getConnectionWrapperForConnID(connID, "getFirstCharDescr");
        Service s = getServiceForConnIDServiceID(connID, serviceID, "getFirstCharDescr");
        
        if (cw==null || s==null || s.callback == null){
            Log.e(TAG, "no callback access, can't discover char descriptors");
            return;
        }
        
        Characteristic c = getCharacteristicFromService(s, charID, "getFirstCharDescr");
        if (c == null) {
            try {
                Log.v(TAG, "no characteristic, can't do anythng");
                s.callback.onGetFirstCharacteristicDescriptor(connID, BleConstants.GATT_ERROR, serviceID, charID, null);
            } catch (RemoteException e) {
                Log.e(TAG, "error when doing onGetFirstCharacteristicDescriptor");
            }
            return;    
        }
    
        c.lastDescriptorStatus = null;
        c.descriptors.clear();
        cw.lastService = s;
        s.lastChar = c;
        if (c.end==0xffff)
            c.end=s.end;
        c.lastDescriptor = null;
        
        synchronized (cw.mGattTool){
            cw.mGattTool
                    .characteristicsDescriptorDiscovery(c.handle + 1, c.end);
            try {
                cw.mGattTool.wait();
            } catch (InterruptedException e) {
                Log.e(TAG, "getFirstCharDescr interrupted");
            }  
        }
    }
    
    @Override
    /**
     * GattToolWrapper callback called for each descriptor discovered
     */
    public void characteristicDescriptor(GattToolWrapper w, int connID, int handle, BleGattID uuid) {
        Log.v(TAG, "characteristicDescriptor " + connID + " " + handle + " " + uuid);        
        ConnectionWrapper cw = getConnectionWrapperForConnID(connID, "getFirstCharDescr");
        if (cw == null) return;
        Service s = cw.lastService;
        if (s == null) return;
        Characteristic c = s.lastChar;
        if (c == null) return;
        if (c.lastDescriptorStatus!=null){
            Log.v(TAG, "ignoring spurious descriptor");
            return;
        }
        
        if (handle!=c.value_handle) {
            c.addDescriptor(new Descriptor(handle, uuid));
            Log.v(TAG, "added char-desc");
        } else
            c.addValueAttribute(new Attribute(handle, uuid));
            Log.e(TAG, "ignoring value handle as descriptor");
        
    }

    @Override
    /**
     * GattToolWrapper callback called when descriptor discovery ended.
     */
    public void characteristicDescriptorEnd(GattToolWrapper w, int connID, int status) {
        Log.v(TAG, "characteristicEnd " + connID + " " + status);
        
        ConnectionWrapper cw = getConnectionWrapperForConnID(connID, "characteristicDescriptorEnd callback");
        if ( cw == null) {
            Log.e(TAG, "no connectin wrapper");
            w.notifyAll();
            return;
        }
        
        if (cw.lastService == null || cw.lastService.lastChar == null){
            Log.e(TAG, "either ConnectionWrapper, lastService or lastChar are null ignoring");
            w.notifyAll();
            return;
        }
        Service s = cw.lastService;
        Characteristic c = s.lastChar;
        
        if (c.lastDescriptorStatus!=null){
            Log.v(TAG, "ignoring spurious descriptor end");
            w.notifyAll();
            return;
        }
        
        c.lastDescriptorStatus = status;
        
        BleGattID uuid = null;
        
        if (c.descriptors.size() > 0)
            uuid = c.descriptors.get(0).uuid;

        w.notifyAll();
        try {
            s.callback.onGetFirstCharacteristicDescriptor(connID, status, 
                    s.uuid, c.uuid, uuid);
        } catch (RemoteException e) {
            Log.e(TAG, "error while onGetFirstCharacteristicDescriptor");
        }
    }

    @Override
    public void getNextCharDescr(int connID,
            BluetoothGattCharDescrID charDescrID, BluetoothGattID id) {
        Log.v(TAG, "getNextCharDescr " + connID + " " + charDescrID + " " + id);
        BluetoothGattID serviceID = charDescrID.getSrvcId();
        BluetoothGattID charID = charDescrID.getCharId();
        BluetoothGattID descID = charDescrID.getDescrId();

        ConnectionWrapper cw = getConnectionWrapperForConnID(connID,
                "getNextCharDescr");
        Service s = getServiceForConnIDServiceID(connID, serviceID,
                "getNextCharDescr");

        if (cw == null || s == null || s.callback == null) {
            Log.e(TAG,
                    "no callback access, can't discover more char descriptors");
            return;
        }

        BluetoothGattID uuid = null;
        Characteristic c = getCharacteristicFromService(s, charID,
                "getNextCharDescr");
        if (c != null) {
            if (c.descriptors.size() > descID.getInstanceID() + 1)
                uuid = c.descriptors.get(descID.getInstanceID() + 1).uuid;

        }
        try {
            Log.v(TAG, "doing onGetNextCharacteristicDescriptor with uuid: "
                    + uuid);
            s.callback.onGetNextCharacteristicDescriptor(connID,
                    uuid != null ? BleConstants.GATT_SUCCESS
                            : BleConstants.GATT_ERROR, serviceID, charID, uuid);
        } catch (RemoteException e) {
            Log.e(TAG, "error while onGetNextCharacteristicDescriptor");
        }
    }

    /* **************************************************************************************
     * char and descriptors reading methods
     */
    
    private Characteristic getCharacteristicFromService(Service service, BluetoothGattID charID, String f){
        
        if (service.chars==null){
            Log.e(TAG, "no chars on getCharacteristicFromService from " + f);
            return null;
        }
        if (service.chars.size() < charID.getInstanceID()+1) {
            Log.v(TAG, "count: " + service.chars.size() + " charID instance: " + charID.getInstanceID());
            Log.e(TAG, "failed to find on getCharacteristicFromService from " + f);
            return null;
        }
        return service.chars.get(charID.getInstanceID());
    }
    
    private Descriptor getDescriptorFromCharacteristic(Characteristic c, BluetoothGattID descID, String f){
        if (c.descriptors.size() < descID.getInstanceID()){
            Log.e(TAG, "descriptor out of range at " + f);
            return null;
        }
        return c.descriptors.get(descID.getInstanceID());
    }
    
    /**
     * called by binder client when it wants to get the value for the descriptors
     **/
    @Override
    public synchronized void readCharDescr(int connID, BluetoothGattCharDescrID charDescID, byte authReq) {
        Log.v(TAG, "readCharDescr " + connID + " " + charDescID + " " + authReq);
        BluetoothGattID serviceID = charDescID.getSrvcId();
        BluetoothGattID charID = charDescID.getCharId();
        BluetoothGattID descID = charDescID.getDescrId();
        
        ConnectionWrapper cw = getConnectionWrapperForConnID(connID, "readCharDescr");
        Service s = getServiceForConnIDServiceID(connID, serviceID, "readCharDescr");
        
        if (cw==null || cw.mGattTool == null || s==null || s.callback == null){
            Log.e(TAG, "something is missing can't go on");
            return;
        }
        
        Characteristic c = getCharacteristicFromService(s, charID, "readCharDescr");
        if (c == null) {
            Log.e(TAG, "no characteristic can't go on");
        } else {        
            Descriptor d = getDescriptorFromCharacteristic(c, descID, "readCharDescr");
            if (d == null){
                Log.e(TAG, "no descriptor can't go on");
            } else {
                cw.lastService = s;
                s.lastChar = c;
                c.lastDescriptor = d;
                synchronized (cw.mGattTool) {
                    cw.mGattTool.readCharacteristicByHandle(d.handle);
                    try {
                        cw.mGattTool.wait();
                    } catch (InterruptedException e) {
                        Log.e(TAG, "interrupted on readCharDescr", e);
                    }
                }
                return;
            }
        }
        try {
            s.callback.onReadCharDescriptorValue(connID, BleConstants.GATT_ERROR, serviceID, charID, descID, null);
        } catch (RemoteException e) {
            Log.e(TAG, "failed calling onReadCharDescriptorValue callback", e);
        }
    }
    
    @Override
    /**
     * called by binder client when it wants to get the value of a char
     **/
    public synchronized void readChar(int connID, BluetoothGattCharID charSvcID, byte authReq) {
        Log.v(TAG, "readChar " + connID + " " + charSvcID + " " + authReq);
        BluetoothGattID serviceID = charSvcID.getSrvcId();
        BluetoothGattID charID = charSvcID.getCharId();
        
        ConnectionWrapper cw = getConnectionWrapperForConnID(connID, "readChar");
        Service s = getServiceForConnIDServiceID(connID, serviceID, "readChar");
        
        if (cw==null || cw.mGattTool == null || s==null || s.callback == null){
            Log.e(TAG, "something is missing can't go on");
            return;
        }
        
        Characteristic c = getCharacteristicFromService(s, charID, "readChar");
        if (c == null) {
            Log.e(TAG, "no characteristic can't go on");
            try {
                s.callback.onReadCharacteristicValue(connID, BleConstants.GATT_ERROR, serviceID, charID, null);
            } catch (RemoteException e) {
                Log.e(TAG, "failed calling onReadCharDescriptorValue callback", e);
            }
            return;
        }
        
        cw.lastService = s;
        s.lastChar = c;
        c.lastDescriptor = null;
        synchronized (cw.mGattTool) {
            cw.mGattTool.readCharacteristicByHandle(c.value_handle);
            try {
                cw.mGattTool.wait();
            } catch (InterruptedException e) {
                Log.e(TAG, "interrupted on readCharDescr", e);
            }
        }
    }
    
    @Override
    /**
     * This callback gets called when GattToolWrapper was able to resolve a value.
     */
    public void gotValueByHandle(GattToolWrapper w, int connID, byte[] value, int status) {
        Log.v(TAG, "gotValueByHandle " + connID +" " + status +" got" + value);
        ConnectionWrapper cw = getConnectionWrapperForConnID(connID, "gotValueByHandle");
        if (cw==null) {
            w.notifyAll();
            return;
        }
        Service s = cw.lastService;
        Characteristic c = s.lastChar;
        Descriptor d = c.lastDescriptor;
        
        w.notifyAll();
        if (d != null) {
            try {
                Log.v(TAG, "calling onReadCharDescriptorValue " + status + " " + d.uuid);
                s.callback.onReadCharDescriptorValue(connID, status, s.uuid, c.uuid, d.uuid, value);
            } catch (RemoteException e) {
                Log.e(TAG, "error when calling onReadCharDescriptorValue", e);
            }
        } else {
            try {
                Log.v(TAG, "calling onReadCharacteristicValue");
                s.callback.onReadCharacteristicValue(connID, status, s.uuid, c.uuid, value);
            } catch (RemoteException e) {
                Log.e(TAG, "error when calling onReadCharacteristicValue", e);
            }
        }

        // cleanup stack.
        c.lastDescriptor = null;
        s.lastChar = null;
        cw.lastService = null;
    }
    
    @Override
    public void gotWriteResult(GattToolWrapper w, int connID, int status) {
        Log.v(TAG, "gotWriteResult");
        w.notifyAll();
    }
    
    @Override
    public void gotWriteResultReq(GattToolWrapper w, int connID, int status) {
        Log.v(TAG, "gotWriteResultReq");
        
        Log.v(TAG, "gotWriteResultReq " + connID +" " + status);
        ConnectionWrapper cw = getConnectionWrapperForConnID(connID, "gotWriteResultReq");
        if (cw==null) {
            w.notifyAll();
            return;
        }
        Service s = cw.lastService;
        Characteristic c = s.lastChar;
        Descriptor d = c.lastDescriptor;
        
        w.notifyAll();
        try {
            if (d!=null){
                s.callback.onWriteCharDescrValue(connID, status, s.uuid, c.uuid, d.uuid);
            } else {
                s.callback.onWriteCharValue(connID, status, s.uuid, c.uuid);
            }
        } catch (RemoteException e) {
            Log.v(TAG, "failed to send onWriteCharDescrValue or onWriteCharValue");
        }
        
    }
    
    @Override
    public synchronized void writeCharValue(int connID, BluetoothGattCharID charSvcID, int writeType, byte authReq,
            byte[] value) {    
        Log.v(TAG, "writeCharValue " + connID + " " + charSvcID + " wryte=" + writeType + " auth=" + authReq);
        BluetoothGattID serviceID = charSvcID.getSrvcId();
        BluetoothGattID charID = charSvcID.getCharId();
        
        ConnectionWrapper cw = getConnectionWrapperForConnID(connID, "writeCharValue");
        Service s = getServiceForConnIDServiceID(connID, serviceID, "writeCharValue");
        
        if (cw==null || cw.mGattTool == null || s==null || s.callback == null){
            Log.e(TAG, "something is missing can't go on");
            return;
        }
        
        Characteristic c = getCharacteristicFromService(s, charID, "writeCharValue");
        if (c == null) {
            Log.e(TAG, "no characteristic can't go on");
            try {
                s.callback.onWriteCharValue(connID, BleConstants.GATT_ERROR, s.uuid, null);
            } catch (RemoteException e) {
                Log.e(TAG, "error when calling onReadCharDescriptorValue", e);
            }
            return;
        }
        
        boolean ret = false;
        if (writeType == BleConstants.GATTC_TYPE_WRITE)
            ret = cw.mGattTool.writeCharReq(c.value_handle, value);
        else if (writeType == BleConstants.GATTC_TYPE_WRITE_NO_RSP)
            ret = cw.mGattTool.writeCharCmd(c.value_handle, value);
        if (ret){
            cw.lastService = s;
            s.lastChar = c;
            c.lastDescriptor = null;
            synchronized (cw.mGattTool) {
                try {
                    cw.mGattTool.wait();
                } catch (InterruptedException e) {
                    Log.e(TAG, "interrupted while writeCharValue");
                }
            }
        } else {
            try {
                Log.e(TAG, "informing write couldn't start");
                s.callback.onWriteCharValue(connID, BleConstants.GATT_BUSY, serviceID, charID);
            } catch (RemoteException e) {
                Log.e(TAG, "error while doing onWriteCharValue callback", e);
            }
        }
        Log.v(TAG, "writeCharValue end");
    }
    
    @Override
    public synchronized void writeCharDescrValue(int connID, BluetoothGattCharDescrID charDescID, int writeType,
            byte authReq, byte[] value) {
        Log.v(TAG, "writeCharDescrValue " + connID + " " + charDescID + " wryte=" + writeType + " auth=" + authReq);
        BluetoothGattID serviceID = charDescID.getSrvcId();
        BluetoothGattID charID = charDescID.getCharId();
        BluetoothGattID descID = charDescID.getDescrId();
        
        ConnectionWrapper cw = getConnectionWrapperForConnID(connID, "writeCharDescrValue");
        Service s = getServiceForConnIDServiceID(connID, serviceID, "writeCharDescrValue");
        
        if (cw==null || cw.mGattTool == null || s==null || s.callback == null){
            Log.e(TAG, "something is missing can't go on");
            return;
        }
        
        Characteristic c = getCharacteristicFromService(s, charID, "writeCharDescrValue");
        if (c == null) {
            Log.e(TAG, "no characteristic can't go on");
            return;
        }
        
        Descriptor d = getDescriptorFromCharacteristic(c, descID, "writeCharDescrValue");
        
        boolean ret = false;
        if (writeType == BleConstants.GATTC_TYPE_WRITE)
            ret = cw.mGattTool.writeCharReq(d.handle, value);
        else if (writeType == BleConstants.GATTC_TYPE_WRITE_NO_RSP)
            ret = cw.mGattTool.writeCharCmd(d.handle, value);
        if (ret){
            cw.lastService = s;
            s.lastChar = c;
            c.lastDescriptor = d;
            synchronized (cw.mGattTool) {
                try {
                    cw.mGattTool.wait();
                } catch (InterruptedException e) {
                    Log.e(TAG, "interrupted while writeCharValue");
                }
            }
        } else { 
            try {
                Log.e(TAG, "informing write couldn't start");
                s.callback.onWriteCharDescrValue(connID, BleConstants.GATT_BUSY, serviceID, charID, descID);
            } catch (RemoteException e) {
                Log.e(TAG, "error while doing onWriteCharDescrValue callback", e);
            }
        }
        Log.v(TAG, "writeCharDescrValue end");

    }
    
    private List<NotificationListener> mListener = new Vector<NotificationListener>();
    
    class NotificationListener {
        AppWrapper appWrapper;
        String address;
        BluetoothGattCharID uuid;
        boolean enabled;
        
        public NotificationListener(AppWrapper w, String a, BluetoothGattCharID u){
            this.appWrapper = w;
            this.address = a;
            this.uuid = u;
            this.enabled = false;
            mListener.add(this);
        }
    }
    
    @Override
    public synchronized boolean registerForNotifications(byte ifaceID, String address,
            BluetoothGattCharID charID) {
        Log.v(TAG, "registerForNotification " + ifaceID + ", " + address + ", " + charID);
        AppWrapper a = null;
        
        for (AppWrapper b: knownApps){
            if (b.mIfaceID == ifaceID)
                a = b;
        }
        
        if (a == null) {
            Log.v(TAG, "app not known can't register");
            return false;
        }
        
        NotificationListener n = new NotificationListener(a, address, charID);
        n.enabled = true;
        
        return true;
    }

    @Override
    public synchronized boolean deregisterForNotifications(byte ifaceID, String address,
            BluetoothGattCharID charID) {
        Log.v(TAG, "deregisterForNotifications " + ifaceID);
        NotificationListener a = null;
        for (NotificationListener n: mListener){
            if (n.appWrapper.mIfaceID == ifaceID)
                a = n;
        }
        if (a == null){
            Log.v(TAG, "not known app id");
            return false;
        }
        
        a.enabled = false;
        return true;
    }
    
    @Override
    public void onNotification(GattToolWrapper w, int conn_handle, int handle, byte[] value) {
        Log.v(TAG, "onNotification " + conn_handle + toIntHexString(handle));
        
        ConnectionWrapper conn = getConnectionWrapperForConnID(conn_handle, "onNotification");
        
        if (conn == null){
            w.notifyAll();
            Log.e(TAG, "no connection wrapper can't do much at onNotification");
            return;
        }
        
        String remote = conn.remote;
        
        Log.v(TAG, "looking for notification handlers for " + remote );
        
        for (NotificationListener nl: mListener){
            if (!nl.address.toLowerCase().equals(remote.toLowerCase()))
                continue;
            Log.v(TAG, "found a connection wrapper for this address");
            if (!nl.enabled){
                Log.v(TAG, "disabled ignoring");
                continue;
            }
            if (!conn.mAttributesByHandle.containsKey(handle)) {
                Log.v(TAG, "connection doesn't know about this attribute handle");
                continue;
            }
            
            Attribute a = conn.mAttributesByHandle.get(handle);
            if (!a.uuid.equals(nl.uuid)){
                Log.v(TAG, "uuid mismatch handler doesn't match the uuid we're tracking ");
                Log.v(TAG, "attribute: " + a.uuid);
                Log.v(TAG, "listener:  " + nl.uuid);
                continue;
            }
            try {
                w.notifyAll();
                a.service.callback.onNotify(conn_handle, remote, a.service.uuid, 
                        a.uuid, true, value);
            } catch (RemoteException e1) {
                Log.v(TAG, "error while doing onNotify", e1);
            }
    
        }
        Log.v(TAG, "notification completed");
    }

    @Override
    public int getApiLevel() {
        return API_LEVEL;
    }

    @Override
    public String getFrameworkVersion() {
        return FRAMEWORK_VERSION;
    }

    @Override
    public void setScanParameters(int scanInterval, int scanWindow) {
        // TODO Auto-generated method stub

        Log.v(TAG, "NI setscanparam\n");
        System.exit(0);
    }

    @Override
    public void filterEnable(boolean p) {
        // TODO Auto-generated method stub

        Log.v(TAG, "NI filterenable\n");
        System.exit(0);

    }

    @Override
    public void filterEnableBDA(boolean enable, int addr_type, String address) {
        // TODO Auto-generated method stub

        Log.v(TAG, "NI filterenablebda\n");
        System.exit(0);

    }

    @Override
    public void clearManufacturerData() {
        // TODO Auto-generated method stub

        Log.v(TAG, "NI clearmanufcdata\n");
        System.exit(0);

    }

    @Override
    public void filterManufacturerData(int company, byte[] data1, byte[] data2, byte[] data3,
            byte[] data4) {
        // TODO Auto-generated method stub

        Log.v(TAG, "NI filtermanufacdata\n");
        System.exit(0);

    }

    @Override
    public void filterManufacturerDataBDA(int company, byte[] data1, byte[] data2, byte[] data3,
            byte[] data4, boolean has_bda, int addr_type, String address) {
        // TODO Auto-generated method stub

        Log.v(TAG, "NI filtermanufdataBDA\n");
        System.exit(0);

    }

    @Override
    public void observe(boolean start, int duration) {
        // TODO Auto-generated method stub

        Log.v(TAG, "NI observe\n");
        System.exit(0);

    }

    @Override
    public void getFirstIncludedService(int connID, BluetoothGattID serviceID, BluetoothGattID id2) {
        // TODO Auto-generated method stub

        Log.v(TAG, "NI getFirstIncServ\n");
        System.exit(0);

    }

    @Override
    public void getNextIncludedService(int connID, BluetoothGattInclSrvcID includedServiceID,
            BluetoothGattID id) {
        // TODO Auto-generated method stub

        Log.v(TAG, "NI getNextIncServ\n");
        System.exit(0);

    }

    @Override
    public void sendIndConfirm(int connID, BluetoothGattCharID charID) {
        // TODO Auto-generated method stub

        Log.v(TAG, "NI sendindconfig\n");
        System.exit(0);

    }

    @Override
    public void prepareWrite(int paramInt1, BluetoothGattCharID charID, int paramInt2,
            int paramInt3, byte[] paramArrayOfByte) {
        // TODO Auto-generated method stub

        Log.v(TAG, "NI prepwriter\n");
        System.exit(0);

    }

    @Override
    public void executeWrite(int paramInt, boolean paramBoolean) {
        // TODO Auto-generated method stub

        Log.v(TAG, "NI execwriter\n");
        System.exit(0);

    }

    @Override
    public void registerServerServiceCallback(BluetoothGattID id1, BluetoothGattID id2,
            IBleServiceCallback callback) {
        // TODO Auto-generated method stub

        Log.v(TAG, "NI regServiceCallback\n");
        System.exit(0);

    }

    @Override
    public void registerServerProfileCallback(BluetoothGattID id, IBleProfileEventCallback callback) {
        // TODO Auto-generated method stub

        Log.v(TAG, "NI regServerProfCallback\n");
        System.exit(0);

    }

    @Override
    public void unregisterServerServiceCallback(int paramInt) {
        // TODO Auto-generated method stub

        Log.v(TAG, "NI unregServServcall\n");
        System.exit(0);

    }

    @Override
    public void unregisterServerProfileCallback(int paramInt) {
        // TODO Auto-generated method stub

        Log.v(TAG, "NI unrefServerProfCallback\n");
        System.exit(0);

    }

    @Override
    public void GATTServer_CreateService(byte paramByte, BluetoothGattID id, int paramInt) {
        // TODO Auto-generated method stub
        System.exit(0);

    }

    @Override
    public void GATTServer_AddIncludedService(int paramInt1, int paramInt2) {
        // TODO Auto-generated method stub
        System.exit(0);

    }

    @Override
    public void GATTServer_AddCharacteristic(int paramInt1, BluetoothGattID id, int paramInt2,
            int paramInt3, boolean paramBoolean, int paramInt4) {
        // TODO Auto-generated method stub
        System.exit(0);

    }

    @Override
    public void GATTServer_AddCharDescriptor(int paramInt1, int paramInt2, BluetoothGattID id) {
        // TODO Auto-generated method stub
        System.exit(0);

    }

    @Override
    public void GATTServer_DeleteService(int paramInt) {
        // TODO Auto-generated method stub
        System.exit(0);

    }

    @Override
    public void GATTServer_StartService(int paramInt, byte paramByte) {
        // TODO Auto-generated method stub
        System.exit(0);

    }

    @Override
    public void GATTServer_StopService(int paramInt) {
        // TODO Auto-generated method stub
        System.exit(0);

    }

    @Override
    public void GATTServer_HandleValueIndication(int paramInt1, int paramInt2,
            byte[] paramArrayOfByte) {
        // TODO Auto-generated method stub
        System.exit(0);

    }

    @Override
    public void GATTServer_HandleValueNotification(int paramInt1, int paramInt2,
            byte[] paramArrayOfByte) {
        // TODO Auto-generated method stub
        System.exit(0);

    }

    @Override
    public void GATTServer_SendRsp(int paramInt1, int paramInt2, byte paramByte1, int paramInt3,
            int paramInt4, byte[] paramArrayOfByte, byte paramByte2, boolean paramBoolean) {
        // TODO Auto-generated method stub
        System.exit(0);

    }

    @Override
    public void GATTServer_Open(byte paramByte, String paramString, boolean paramBoolean) {
        // TODO Auto-generated method stub
        System.exit(0);

    }

    @Override
    public void GATTServer_CancelOpen(byte paramByte, String paramString, boolean paramBoolean) {
        // TODO Auto-generated method stub
        System.exit(0);

    }

    @Override
    public void GATTServer_Close(int paramInt) {
        // TODO Auto-generated method stub
        System.exit(0);

    }

    @Override
    public void onIndication(GattToolWrapper w, int conn_handle, int handle, byte[] value) {
        // TODO Auto-generated method stub
        Log.v(TAG, "onIndictation");
        w.notifyAll();
    }

    @Override
    public void gotValueByUuid(GattToolWrapper w, int conn_handle, int handle, byte[] value) {
        // TODO Auto-generated method stub
        Log.v(TAG, "gotValueByUuid");
        w.notifyAll();
    }

    @Override
    public void gotValueByUuidEnd(GattToolWrapper w, int conn_handle, int status) {
        // TODO Auto-generated method stub
        Log.v(TAG, "gotValueByUuidEnd");
        w.notifyAll();
    }

    @Override
    public void gotMtuResult(GattToolWrapper w, int connID, int status) {
        Log.v(TAG, "gotMtuResult");
        w.notifyAll();
    }

    @Override
    public void gotPsmResult(GattToolWrapper w, int psm) {
        Log.v(TAG, "gotPsmResult " + psm +  " " + w);
        w.notifyAll();
    }

    @Override
    public void processExit(GattToolWrapper w, int retcode) {
        // TODO Auto-generated method stub
        Log.e(TAG, "processExit with retcode " + retcode + " !!!!!!!!");
        w.notifyAll();
    }

    @Override
    public void processStdinClosed(GattToolWrapper w) {
        // TODO Auto-generated method stub
        Log.v(TAG, "processStdinClosed");
        w.notifyAll();
    }

    @Override
    public void shellError(GattToolWrapper w, SHELL_ERRORS e) {
        // TODO Auto-generated method stub
        Log.v(TAG, "shellError");
        w.notifyAll();
    }
    
    //
    // The following methods are inherited from BlueZInterface.Listener
    // they should be dropped once we get all working with gatttool-btle
    // TODO: drop this completly!.
    //
    
    @Override
    public void serviceDiscovered(int connID, String address, String uuid, String path) {
       Log.e(TAG, "ignoring serviceDiscovered");
    }

    @Override
    public void serviceDiscoveredFinished(int connID, int status) {
        Log.e(TAG, "ignoring serviceDiscoveredFinished");
    }

    @Override
    public void characteristicsSolved(int connID, String serPath, List<Path> chars,
            List<BluetoothGattID> uuids) {
        Log.e(TAG, "ignoring characteristicSolved");
    }
    

    @Override
    public void valueChanged(String path, byte[] val) {
       Log.e(TAG, "ignoring valueChanged");
    }
}
