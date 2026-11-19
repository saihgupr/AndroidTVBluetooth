package com.saihgupr.btcontrol.sony;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public final class SonyFrame {

    public static final int TYPE_COMMAND = 0x0C;
    public static final int TYPE_ACK = 0x01;

    static final int MODE_OFF = 0;
    static final int MODE_NC = 1;
    static final int MODE_AMBIENT = 2;

    private static final int START = 0x3E;
    private static final int END = 0x3C;
    private static final int ESCAPE = 0x3D;
    private static final int MAX_BODY = 1024;

    private SonyFrame() {
    }

    public static byte[] encode(int type, int seq, byte[] payload) {
        byte[] data = payload == null ? new byte[0] : payload;
        int length = data.length;
        int sum = (type & 0xFF) + (seq & 0xFF)
                + ((length >>> 24) & 0xFF)
                + ((length >>> 16) & 0xFF)
                + ((length >>> 8) & 0xFF)
                + (length & 0xFF);
        for (byte b : data) {
            sum += b & 0xFF;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(length + 8);
        out.write(START);
        writeEscaped(out, type);
        writeEscaped(out, seq);
        writeEscaped(out, length >>> 24);
        writeEscaped(out, length >>> 16);
        writeEscaped(out, length >>> 8);
        writeEscaped(out, length);
        for (byte b : data) {
            writeEscaped(out, b);
        }
        writeEscaped(out, sum);
        out.write(END);
        return out.toByteArray();
    }

    public static byte[] ack(int seq) {
        return encode(TYPE_ACK, seq, new byte[0]);
    }

    static byte[] payloadOf(byte[] body) {
        int length = payloadLength(body);
        if (length < 0) {
            return new byte[0];
        }
        byte[] payload = new byte[length];
        System.arraycopy(body, 6, payload, 0, length);
        return payload;
    }

    static State parseState(byte[] payload) {
        if (payload == null || payload.length < 8 || (payload[0] & 0xFF) != 0x67) {
            return null;
        }
        int effect = payload[2] & 0xFF;
        int ncType = payload[3] & 0xFF;
        int ncValue = payload[4] & 0xFF;
        int asmType = payload[5] & 0xFF;
        int level = payload[7] & 0xFF;

        int mode;
        if (effect == 0) {
            mode = MODE_OFF;
        } else if (ncValue != 0) {
            mode = MODE_NC;
        } else if (level > 0) {
            mode = MODE_AMBIENT;
        } else {
            mode = MODE_OFF;
        }
        return new State(mode, ncType, asmType, level);
    }

    private static int payloadLength(byte[] body) {
        if (body == null || body.length < 7) {
            return -1;
        }
        int length = ((body[2] & 0xFF) << 24)
                | ((body[3] & 0xFF) << 16)
                | ((body[4] & 0xFF) << 8)
                | (body[5] & 0xFF);
        if (length < 0 || length > MAX_BODY || body.length != length + 7) {
            return -1;
        }
        return length;
    }

    private static boolean valid(byte[] body) {
        int length = payloadLength(body);
        if (length < 0) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < length + 6; i++) {
            sum += body[i] & 0xFF;
        }
        return (sum & 0xFF) == (body[length + 6] & 0xFF);
    }

    private static void writeEscaped(ByteArrayOutputStream out, int value) {
        int b = value & 0xFF;
        if (b == START || b == END || b == ESCAPE) {
            out.write(ESCAPE);
            out.write(b & 0xEF);
        } else {
            out.write(b);
        }
    }

    static final class State {
        final int mode;
        final int ncType;
        final int asmType;
        final int ambientLevel;

        State(int mode, int ncType, int asmType, int ambientLevel) {
            this.mode = mode;
            this.ncType = ncType;
            this.asmType = asmType;
            this.ambientLevel = ambientLevel;
        }
    }

    public static final class Decoder {
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();
        private boolean reading;
        private boolean escaping;

        public List<byte[]> feed(byte[] data, int len) {
            List<byte[]> frames = new ArrayList<>();
            if (data == null || len <= 0) {
                return frames;
            }
            int count = Math.min(len, data.length);
            for (int i = 0; i < count; i++) {
                int b = data[i] & 0xFF;
                if (!reading) {
                    if (b == START) {
                        reading = true;
                        body.reset();
                        escaping = false;
                    }
                    continue;
                }
                if (b == END) {
                    reading = false;
                    escaping = false;
                    byte[] candidate = body.toByteArray();
                    body.reset();
                    if (valid(candidate)) {
                        frames.add(candidate);
                    }
                    continue;
                }
                if (escaping) {
                    escaping = false;
                    body.write(b | 0x10);
                    continue;
                }
                if (b == ESCAPE) {
                    escaping = true;
                    continue;
                }
                if (b == START) {
                    body.reset();
                    escaping = false;
                    continue;
                }
                body.write(b);
                if (body.size() > MAX_BODY) {
                    reading = false;
                    body.reset();
                }
            }
            return frames;
        }
    }
}
