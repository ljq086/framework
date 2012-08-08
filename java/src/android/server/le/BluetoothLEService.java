/*
 * Copyright (C) 2012 Naranjo Manuel Francisco
 * Copyright (c) 2011-2012, Code Aurora Forum. All rights reserved
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

package android.server.le;

import android.bluetooth.BluetoothAdapter;
import android.util.Log;
import android.os.ServiceManager;
import android.os.Looper;
import android.os.Process;

import com.android.internal.os.BinderInternal;

class ServerThread extends Thread {
    private static final String TAG = "BTLE-Server";
    private static final boolean DBG = false;

    @Override
    public void run(){
        Looper.prepare();

        Process.setThreadPriority(
                Process.THREAD_PRIORITY_FOREGROUND);
        
        BinderInternal.disableBackgroundScheduling(true);
        Process.setCanSelfBackground(false);
        
        try {
            Log.i(TAG, "Starting Bluetooth Service");
        } catch ( RuntimeException e) {
            Log.e(TAG, "failed to start service", e);
        }

        Looper.loop();
    }
};

public class BluetoothLEService {
    private static final String TAG = "BluetoothLEService";
    private static final boolean DBG = false;
    
    private BluetoothAdapter mAdapter;
    
    static {
        Log.i(TAG, "static init");
    }

    public static final void init() {
        Log.i(TAG, "Starting service thread!");
        Thread thr = new ServerThread();
        thr.setName("android.server.le.ServiceThread");
        thr.start();
    }

    public static void main(String[] args)
    {
        Log.i(TAG, "sucker");
        init();                   
    }
}
