/************************************************************************************
 *
 *  Copyright (C) 2012      Naranjo Manuel Francisco <naranjo.manuel@gmail.com>
 *  Copyright (C) 2009-2011 Broadcom Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ************************************************************************************/

package com.broadcom.bt.le.api;

/**
 * Contains a collection of GATT related constants used by Bluetooth Low Energy
 * profiles and services. This is a purely static class containing the constant
 * definitions.
 */
public abstract class BleConstants
{
    public static final int GATT_UNDEFINED = -1;
    public static final int GATT_SERVICE_CREATION_SUCCESS = 0;
    public static final int GATT_SERVICE_CREATION_FAILED = 1;
    public static final int GATT_SERVICE_START_SUCCESS = 2;
    public static final int GATT_SERVICE_START_FAILED = 3;
    public static final int GATT_SERVICE_STOPPED = 4;
    public static final int SERVICE_UNAVAILABLE = 1;
    public static final int GATT_SERVICE_PRIMARY = 0;
    public static final int GATT_SERVICE_SECONDARY = 1;
    public static final int GATT_SERVER_PROFILE_INITIALIZED = 0;
    public static final int GATT_SERVER_PROFILE_UP = 1;
    public static final int GATT_SERVER_PROFILE_DOWN = 2;
    public static final int GATT_SUCCESS = 0;
    public static final int GATT_INVALID_HANDLE = 1;
    public static final int GATT_READ_NOT_PERMIT = 2;
    public static final int GATT_WRITE_NOT_PERMIT = 3;
    public static final int GATT_INVALID_PDU = 4;
    public static final int GATT_INSUF_AUTHENTICATION = 5;
    public static final int GATT_REQ_NOT_SUPPORTED = 6;
    public static final int GATT_INVALID_OFFSET = 7;
    public static final int GATT_INSUF_AUTHORIZATION = 8;
    public static final int GATT_PREPARE_Q_FULL = 9;
    public static final int GATT_NOT_FOUND = 10;
    public static final int GATT_NOT_LONG = 11;
    public static final int GATT_INSUF_KEY_SIZE = 12;
    public static final int GATT_INVALID_ATTR_LEN = 13;
    public static final int GATT_ERR_UNLIKELY = 14;
    public static final int GATT_INSUF_ENCRYPTION = 15;
    public static final int GATT_UNSUPPORT_GRP_TYPE = 16;
    public static final int GATT_INSUF_RESOURCE = 17;
    public static final int GATT_ILLEGAL_PARAMETER = 135;
    public static final int GATT_NO_RESOURCES = 128;
    public static final int GATT_INTERNAL_ERROR = 129;
    public static final int GATT_WRONG_STATE = 130;
    public static final int GATT_DB_FULL = 131;
    public static final int GATT_BUSY = 132;
    public static final int GATT_ERROR = 133;
    public static final int GATT_CMD_STARTED = 134;
    public static final int GATT_PENDING = 136;
    public static final int GATT_AUTH_FAIL = 137;
    public static final int GATT_MORE = 138;
    public static final int GATT_INVALID_CFG = 139;
    public static final byte GATT_AUTH_REQ_NONE = 0;
    public static final byte GATT_AUTH_REQ_NO_MITM = 1;
    public static final byte GATT_AUTH_REQ_MITM = 2;
    public static final byte GATT_AUTH_REQ_SIGNED_NO_MITM = 3;
    public static final byte GATT_AUTH_REQ_SIGNED_MITM = 4;
    public static final int GATT_PERM_READ = 1;
    public static final int GATT_PERM_READ_ENCRYPTED = 2;
    public static final int GATT_PERM_READ_ENC_MITM = 4;
    public static final int GATT_PERM_WRITE = 16;
    public static final int GATT_PERM_WRITE_ENCRYPTED = 32;
    public static final int GATT_PERM_WRITE_ENC_MITM = 64;
    public static final int GATT_PERM_WRITE_SIGNED = 128;
    public static final int GATT_PERM_WRITE_SIGNED_MITM = 256;
    public static final byte GATT_CHAR_PROP_BIT_BROADCAST = 1;
    public static final byte GATT_CHAR_PROP_BIT_READ = 2;
    public static final byte GATT_CHAR_PROP_BIT_WRITE_NR = 4;
    public static final byte GATT_CHAR_PROP_BIT_WRITE = 8;
    public static final byte GATT_CHAR_PROP_BIT_NOTIFY = 16;
    public static final byte GATT_CHAR_PROP_BIT_INDICATE = 32;
    public static final byte GATT_CHAR_PROP_BIT_AUTH = 64;
    public static final byte GATT_CHAR_PROP_BIT_EXT_PROP = -128;
    public static final byte SVC_INF_INVALID = -1;
    public static final int GATTC_TYPE_WRITE_NO_RSP = 1;
    public static final int GATTC_TYPE_WRITE = 2;
    public static final int GATT_FORMAT_RES = 0;
    public static final int GATT_FORMAT_BOOL = 1;
    public static final int GATT_FORMAT_2BITS = 2;
    public static final int GATT_FORMAT_NIBBLE = 3;
    public static final int GATT_FORMAT_UINT8 = 4;
    public static final int GATT_FORMAT_UINT12 = 5;
    public static final int GATT_FORMAT_UINT16 = 6;
    public static final int GATT_FORMAT_UINT24 = 7;
    public static final int GATT_FORMAT_UINT32 = 8;
    public static final int GATT_FORMAT_UINT48 = 9;
    public static final int GATT_FORMAT_UINT64 = 10;
    public static final int GATT_FORMAT_UINT128 = 11;
    public static final int GATT_FORMAT_SINT8 = 12;
    public static final int GATT_FORMAT_SINT12 = 13;
    public static final int GATT_FORMAT_SINT16 = 14;
    public static final int GATT_FORMAT_SINT24 = 15;
    public static final int GATT_FORMAT_SINT32 = 16;
    public static final int GATT_FORMAT_SINT48 = 17;
    public static final int GATT_FORMAT_SINT64 = 18;
    public static final int GATT_FORMAT_SINT128 = 19;
    public static final int GATT_FORMAT_FLOAT32 = 20;
    public static final int GATT_FORMAT_FLOAT64 = 21;
    public static final int GATT_FORMAT_SFLOAT = 22;
    public static final int GATT_FORMAT_FLOAT = 23;
    public static final int GATT_FORMAT_DUINT16 = 24;
    public static final int GATT_FORMAT_UTF8S = 25;
    public static final int GATT_FORMAT_UTF16S = 26;
    public static final int GATT_FORMAT_STRUCT = 27;
    public static final int GATT_FORMAT_MAX = 28;
    public static final String GATT_UUID_CHAR_EXT_PROP = "00002900-0000-1000-8000-00805f9b34fb";
    public static final String GATT_UUID_CHAR_DESCRIPTION = "00002901-0000-1000-8000-00805f9b34fb";
    public static final String GATT_UUID_CHAR_CLIENT_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    public static final String GATT_UUID_CHAR_SRVR_CONFIG = "00002903-0000-1000-8000-00805f9b34fb";
    public static final String GATT_UUID_CHAR_PRESENT_FORMAT = "00002904-0000-1000-8000-00805f9b34fb";
    public static final String GATT_UUID_CHAR_AGG_FORMAT = "00002905-0000-1000-8000-00805f9b34fb";
    public static final int GATT_UUID_CHAR_EXT_PROP16 = 10496;
    public static final int GATT_UUID_CHAR_DESCRIPTION16 = 10497;
    public static final int GATT_UUID_CHAR_CLIENT_CONFIG16 = 10498;
    public static final int GATT_UUID_CHAR_SRVR_CONFIG16 = 10499;
    public static final int GATT_UUID_CHAR_PRESENT_FORMAT16 = 10500;
    public static final int GATT_UUID_CHAR_AGG_FORMAT16 = 10501;
    public static final int GATT_TRANSPORT_BREDR_LE = 2;
    public static final int GATT_TRANSPORT_BREDR = 1;
    public static final int GATT_TRANSPORT_LE = 0;
    public static final int GATT_UUID_TYPE_128 = 16;
    public static final int GATT_UUID_TYPE_32 = 4;
    public static final int GATT_UUID_TYPE_16 = 2;
    public static final int PREPARE_QUEUE_SIZE = 200;
    public static final int GATT_MAX_CHAR_VALUE_LENGTH = 100;
    public static final int GATT_CLIENT_CONFIG_NOTIFICATION_BIT = 1;
    public static final int GATT_CLIENT_CONFIG_INDICATION_BIT = 2;
    public static final int GATT_INVALID_CONN_ID = 65535;
    public static final int VALUE_DIRTY = 1;
    public static final int USER_DESCRIPTION_DIRTY = 2;
    public static final int EXT_PROP_DIRTY = 4;
    public static final int PRESENTATION_FORMAT_DIRTY = 8;
    public static final int CLIENT_CONFIG_DIRTY = 16;
    public static final int SERVER_CONFIG_DIRTY = 32;
    public static final int AGGREGATED_FORMAT_DIRTY = 64;
    public static final int USER_DESCRIPTOR_DIRTY = 128;
    public static final int ALL_DIRTY = 127;
    public static final byte GATT_ENCRYPT_NONE = 0;
    public static final byte GATT_ENCRYPT = 1;
    public static final byte GATT_ENCRYPT_NO_MITM = 2;
    public static final byte GATT_ENCRYPT_MITM = 3;
    static final String ACTION_OBSERVE_RESULT = "com.broadcom.bt.app.gatt.OBSERVE_RESULT";
    static final String ACTION_OBSERVE_COMPLETED = "com.broadcom.bt.app.gatt.OBSERVE_COMPLETED";
    static final String EXTRA_ADDRESS = "ADDRESS";
    static final String EXTRA_ADDR_TYPE = "ADDR_TYPE";
    static final String EXTRA_RSSI = "RSSI";
    static final String EXTRA_ADV_DATA = "ADV_DATA";
    static final String EXTRA_NUM_RESULTS = "NUM_RESULTS";
    static final String GATT_SVC_PKG_NAME = "com.broadcom.bt.app.system";
    static final String GATT_SVC_NAME = "com.broadcom.bt.app.system.GattService";

    /**
     * @hide
     */
    public static final String BLUETOOTH_LE_SERVICE = "com.manuelnaranjo.btle";
}
