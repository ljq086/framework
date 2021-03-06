/*
   D-Bus Java Implementation
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
 */

package org.freedesktop.dbus;

import static org.freedesktop.dbus.Gettext._;

import android.util.Log;

import com.android.internal.util.HexDump;

import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.MessageProtocolVersionException;
import org.freedesktop.dbus.exceptions.MessageTypeException;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.text.MessageFormat;

public class MessageReader
{
    private static final String TAG = "DBus-MR";

    public static final int VERBOSE = Log.VERBOSE;
    public static final int DEBUG = Log.DEBUG;
    public static final int INFO = Log.INFO;
    public static final int WARN = Log.WARN;
    public static final int ERROR = Log.ERROR;
    public static final int ASSERT = Log.ASSERT;
    private static int LEVEL = INFO;

    @SuppressWarnings("unused")
    private static void debug(Throwable o) {
        Log.e(TAG, "error", o);
    }

    @SuppressWarnings("unused")
    private static void debug(int l, Object o) {
        if (l >= LEVEL)
            if (o != null)
                Log.println(l, TAG, o.toString());
            else
                Log.println(l, TAG, "NULL");
    }

    @SuppressWarnings("unused")
    private static void debug(Object o) {
        debug(DEBUG, o);
    }

    private InputStream in;
    private byte[] buf = null;
    private byte[] tbuf = null;
    private byte[] header = null;
    private byte[] body = null;
    private int[] len = new int[4];

    public MessageReader(InputStream in)
    {
        this.in = new BufferedInputStream(in);
    }

    public Message readMessage() throws IOException, DBusException
    {
        int rv;
        /* Read the 12 byte fixed header, retrying as neccessary */
        if (null == buf) {
            buf = new byte[12];
            len[0] = 0;
        }
        if (len[0] < 12) {
            try {
                rv = in.read(buf, len[0], 12 - len[0]);
            } catch (SocketTimeoutException STe) {
                return null;
            }
            if (-1 == rv)
                throw new EOFException(_("Underlying transport returned EOF"));
            len[0] += rv;
        }
        if (len[0] == 0)
            return null;
        if (len[0] < 12) {

            debug(DEBUG, "Only got " + len[0] + " of 12 bytes of header");
            return null;
        }

        /* Parse the details from the header */
        byte endian = buf[0];
        byte type = buf[1];
        byte protover = buf[3];
        if (protover > Message.PROTOCOL) {
            buf = null;
            throw new MessageProtocolVersionException(MessageFormat.format(
                    _("Protocol version {0} is unsupported"), new Object[] {
                        protover
                    }));
        }

        /* Read the length of the variable header */
        if (null == tbuf) {
            tbuf = new byte[4];
            len[1] = 0;
        }
        if (len[1] < 4) {
            try {
                rv = in.read(tbuf, len[1], 4 - len[1]);
            } catch (SocketTimeoutException STe) {
                return null;
            }
            if (-1 == rv)
                throw new EOFException(_("Underlying transport returned EOF"));
            len[1] += rv;
        }
        if (len[1] < 4) {

            debug(DEBUG, "Only got " + len[1] + " of 4 bytes of header");
            return null;
        }

        /* Parse the variable header length */
        int headerlen = 0;
        if (null == header) {
            headerlen = (int) Message.demarshallint(tbuf, 0, endian, 4);
            if (0 != headerlen % 8)
                headerlen += 8 - (headerlen % 8);
        } else
            headerlen = header.length - 8;

        /* Read the variable header */
        if (null == header) {
            header = new byte[headerlen + 8];
            System.arraycopy(tbuf, 0, header, 0, 4);
            len[2] = 0;
        }
        if (len[2] < headerlen) {
            try {
                rv = in.read(header, 8 + len[2], headerlen - len[2]);
            } catch (SocketTimeoutException STe) {
                return null;
            }
            if (-1 == rv)
                throw new EOFException(_("Underlying transport returned EOF"));
            len[2] += rv;
        }
        if (len[2] < headerlen) {

            debug(DEBUG, "Only got " + len[2] + " of " + headerlen
                    + " bytes of header");
            return null;
        }

        /* Read the body */
        int bodylen = 0;
        if (null == body)
            bodylen = (int) Message.demarshallint(buf, 4, endian, 4);
        if (null == body) {
            body = new byte[bodylen];
            len[3] = 0;
        }
        if (len[3] < body.length) {
            try {
                rv = in.read(body, len[3], body.length - len[3]);
            } catch (SocketTimeoutException STe) {
                return null;
            }
            if (-1 == rv)
                throw new EOFException(_("Underlying transport returned EOF"));
            len[3] += rv;
        }
        if (len[3] < body.length) {

            debug(DEBUG, "Only got " + len[3] + " of " + body.length
                    + " bytes of body");
            return null;
        }

        Message m;
        switch (type) {
            case Message.MessageType.METHOD_CALL:
                m = new MethodCall();
                break;
            case Message.MessageType.METHOD_RETURN:
                m = new MethodReturn();
                break;
            case Message.MessageType.SIGNAL:
                m = new DBusSignal();
                break;
            case Message.MessageType.ERROR:
                m = new Error();
                break;
            default:
                throw new MessageTypeException(MessageFormat.format(
                        _("Message type {0} unsupported"), new Object[] {
                            type
                        }));
        }
        {
            debug(VERBOSE, HexDump.dumpHexString(buf));
            debug(VERBOSE, HexDump.dumpHexString(tbuf));
            debug(VERBOSE, HexDump.dumpHexString(header));
            debug(VERBOSE, HexDump.dumpHexString(body));
        }
        try {
            m.populate(buf, header, body);
        } catch (DBusException DBe) {
            debug(DBe);
            buf = null;
            tbuf = null;
            body = null;
            header = null;
            throw DBe;
        } catch (RuntimeException Re) {
            debug(Re);
            buf = null;
            tbuf = null;
            body = null;
            header = null;
            throw Re;
        }
        {
            debug(INFO, "=> " + m);
        }
        buf = null;
        tbuf = null;
        body = null;
        header = null;
        return m;
    }

    public void close() throws IOException
    {

        debug(INFO, "Closing Message Reader");
        in.close();
    }
}
