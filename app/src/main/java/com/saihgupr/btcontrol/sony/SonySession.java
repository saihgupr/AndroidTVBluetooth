package com.saihgupr.btcontrol.sony;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SonySession implements AutoCloseable {

    public enum Mode { NC, AMBIENT, OFF, UNKNOWN }

    public static final class Result {
        public final boolean ok;
        public final Mode mode;
        public final int ambientLevel;
        public final String error;

        private Result(boolean ok, Mode mode, int ambientLevel, String error) {
            this.ok = ok;
            this.mode = mode;
            this.ambientLevel = ambientLevel;
            this.error = error;
        }

        public static Result ok(Mode mode, int ambientLevel) {
            return new Result(true, mode, ambientLevel, null);
        }

        public static Result error(String error) {
            return new Result(false, Mode.UNKNOWN, 0, error);
        }
    }

    public static final String SONY_UUID = "96CC203E-5068-46AD-B32D-E316F5E069BA";

    private static final String TAG = "SonySession";
    private static final long CONNECT_TIMEOUT_MS = 6000;
    private static final long INIT_TIMEOUT_MS = 2000;
    private static final long INIT_SETTLE_MS = 150;
    private static final long GET_TIMEOUT_MS = 2000;
    private static final long SET_SETTLE_MS = 500;
    private static final int MAX_AMBIENT_LEVEL = 0x14;
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();
    private static final byte[] EOF = new byte[0];

    private final BluetoothSocket socket;
    private final InputStream in;
    private final OutputStream out;
    private final LinkedBlockingQueue<byte[]> incoming = new LinkedBlockingQueue<>();
    private final SonyFrame.Decoder decoder = new SonyFrame.Decoder();

    private volatile boolean closed;
    private int seq;
    private int ncType;
    private int asmType;
    private boolean hasState;

    @SuppressWarnings("deprecation")
    public SonySession(BluetoothDevice device) throws IOException {
        BluetoothSocket socket = null;
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter != null) {
                try {
                    adapter.cancelDiscovery();
                } catch (SecurityException e) {
                    Log.w(TAG, "cancelDiscovery denied", e);
                }
            }
            socket = device.createRfcommSocketToServiceRecord(UUID.fromString(SONY_UUID));
            final BluetoothSocket connecting = socket;
            final AtomicBoolean connected = new AtomicBoolean(false);
            Thread watchdog = new Thread(() -> {
                try {
                    Thread.sleep(CONNECT_TIMEOUT_MS);
                } catch (InterruptedException e) {
                    return;
                }
                if (!connected.get()) {
                    Log.w(TAG, "connect timed out");
                    closeQuietly(connecting);
                }
            }, "sony-connect-watchdog");
            watchdog.setDaemon(true);
            watchdog.start();
            try {
                socket.connect();
            } finally {
                connected.set(true);
                watchdog.interrupt();
            }
            this.socket = socket;
            this.in = socket.getInputStream();
            this.out = socket.getOutputStream();
            Thread reader = new Thread(this::readLoop, "sony-reader");
            reader.setDaemon(true);
            reader.start();
            Log.d(TAG, "connected");
        } catch (SecurityException e) {
            closeQuietly(socket);
            throw e;
        } catch (IOException e) {
            closeQuietly(socket);
            throw e;
        }
    }

    public Result readMode() throws IOException {
        sendCommand(new byte[]{0x00, 0x00});
        byte[] initReply = readFrame(System.nanoTime() + INIT_TIMEOUT_MS * 1_000_000L);
        if (initReply != null) {
            handleIncoming(initReply);
        }
        sleepQuietly(INIT_SETTLE_MS);

        sendCommand(new byte[]{0x66, 0x02});
        long deadline = System.nanoTime() + GET_TIMEOUT_MS * 1_000_000L;
        while (true) {
            byte[] body = readFrame(deadline);
            if (body == null) {
                break;
            }
            handleIncoming(body);
            if (!isCommand(body)) {
                continue;
            }
            byte[] payload = SonyFrame.payloadOf(body);
            if (payload.length < 1 || (payload[0] & 0xFF) != 0x67) {
                continue;
            }
            if (payload.length < 8) {
                return Result.error("Unsupported headphones");
            }
            SonyFrame.State state = SonyFrame.parseState(payload);
            if (state == null) {
                continue;
            }
            ncType = state.ncType;
            asmType = state.asmType;
            hasState = true;
            return Result.ok(toMode(state.mode), state.ambientLevel);
        }
        return Result.error("No reply from headphones");
    }

    public Result writeMode(Mode target, int ambientLevel) throws IOException {
        if (!hasState) {
            return Result.error("No reply from headphones");
        }
        Mode mode = target == null || target == Mode.UNKNOWN ? Mode.OFF : target;
        int level = Math.max(0, Math.min(MAX_AMBIENT_LEVEL, ambientLevel));

        byte[] payload;
        if (mode == Mode.AMBIENT) {
            payload = new byte[]{0x68, 0x02, 0x11, (byte) ncType, 0x00, (byte) asmType, 0x00, (byte) level};
        } else if (mode == Mode.NC) {
            payload = new byte[]{0x68, 0x02, 0x11, (byte) ncType, 0x02, (byte) asmType, 0x00, 0x00};
        } else {
            payload = new byte[]{0x68, 0x02, 0x00, (byte) ncType, 0x00, (byte) asmType, 0x00, 0x00};
        }

        sendCommand(payload);
        try {
            pump(SET_SETTLE_MS);
        } catch (IOException e) {
            Log.w(TAG, "settle failed", e);
        }
        return Result.ok(mode, mode == Mode.AMBIENT ? level : 0);
    }

    @Override
    public void close() {
        closed = true;
        closeQuietly(socket);
        incoming.offer(EOF);
    }

    private void readLoop() {
        byte[] buffer = new byte[512];
        try {
            while (!closed) {
                int read = in.read(buffer);
                if (read < 0) {
                    break;
                }
                for (byte[] frame : decoder.feed(buffer, read)) {
                    incoming.offer(frame);
                }
            }
        } catch (IOException e) {
            if (!closed) {
                Log.w(TAG, "read ended", e);
            }
        } finally {
            incoming.offer(EOF);
        }
    }

    private void sendCommand(byte[] payload) throws IOException {
        byte[] frame = SonyFrame.encode(SonyFrame.TYPE_COMMAND, seq, payload);
        out.write(frame);
        out.flush();
        Log.d(TAG, "tx " + toHex(frame));
        seq ^= 1;
    }

    private void sendAck(int receivedSeq) throws IOException {
        byte[] frame = SonyFrame.ack(receivedSeq ^ 1);
        out.write(frame);
        out.flush();
        Log.d(TAG, "tx " + toHex(frame));
    }

    private void handleIncoming(byte[] body) throws IOException {
        Log.d(TAG, "rx " + toHex(body));
        if (!isAck(body)) {
            sendAck(body[1] & 0xFF);
        }
    }

    private void pump(long millis) throws IOException {
        long deadline = System.nanoTime() + millis * 1_000_000L;
        byte[] body;
        while ((body = readFrame(deadline)) != null) {
            handleIncoming(body);
        }
    }

    private byte[] readFrame(long deadlineNanos) {
        long remainingMs = (deadlineNanos - System.nanoTime()) / 1_000_000L;
        if (remainingMs <= 0) {
            return null;
        }
        try {
            byte[] body = incoming.poll(remainingMs, TimeUnit.MILLISECONDS);
            if (body == null || body == EOF) {
                if (body == EOF) {
                    incoming.offer(EOF);
                }
                return null;
            }
            return body;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static boolean isCommand(byte[] body) {
        return body.length >= 2 && (body[0] & 0xFF) == SonyFrame.TYPE_COMMAND;
    }

    private static boolean isAck(byte[] body) {
        return body.length >= 2 && (body[0] & 0xFF) == SonyFrame.TYPE_ACK;
    }

    private static Mode toMode(int mode) {
        if (mode == SonyFrame.MODE_NC) {
            return Mode.NC;
        }
        if (mode == SonyFrame.MODE_AMBIENT) {
            return Mode.AMBIENT;
        }
        return Mode.OFF;
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void closeQuietly(BluetoothSocket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException e) {
            Log.w(TAG, "close failed", e);
        }
    }

    private static String toHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 3);
        for (byte b : data) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(HEX[(b >> 4) & 0x0F]).append(HEX[b & 0x0F]);
        }
        return sb.toString();
    }
}
