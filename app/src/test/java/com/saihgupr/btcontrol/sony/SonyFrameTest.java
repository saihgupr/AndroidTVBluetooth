package com.saihgupr.btcontrol.sony;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class SonyFrameTest {

    private static final byte[] COMMAND_INIT = hex("3E 0C 00 00 00 00 02 00 00 0E 3C");
    private static final byte[] COMMAND_ACK = hex("3E 01 01 00 00 00 00 02 3C");
    private static final byte[] COMMAND_GET = hex("3E 0C 00 00 00 00 02 66 02 76 3C");
    private static final byte[] COMMAND_SET_NC = hex("3E 0C 00 00 00 00 08 68 02 11 02 02 01 00 00 94 3C");
    private static final byte[] COMMAND_SET_AMBIENT = hex("3E 0C 00 00 00 00 08 68 02 11 02 00 01 00 14 A6 3C");

    @Test
    public void initFrameMatchesKnownVector() {
        assertArrayEquals(COMMAND_INIT, SonyFrame.encode(SonyFrame.TYPE_COMMAND, 0, bytes(0x00, 0x00)));
    }

    @Test
    public void nullPayloadIsTreatedAsEmpty() {
        assertArrayEquals(SonyFrame.ack(0), SonyFrame.encode(SonyFrame.TYPE_ACK, 0, null));
    }

    @Test
    public void ackFrameMatchesKnownVector() {
        assertArrayEquals(COMMAND_ACK, SonyFrame.ack(1));
    }

    @Test
    public void getFrameMatchesKnownVector() {
        assertArrayEquals(COMMAND_GET, SonyFrame.encode(SonyFrame.TYPE_COMMAND, 0, bytes(0x66, 0x02)));
    }

    @Test
    public void setNoiseCancellingFrameMatchesKnownVector() {
        byte[] payload = bytes(0x68, 0x02, 0x11, 0x02, 0x02, 0x01, 0x00, 0x00);
        assertArrayEquals(COMMAND_SET_NC, SonyFrame.encode(SonyFrame.TYPE_COMMAND, 0, payload));
    }

    @Test
    public void setAmbientFrameMatchesKnownVector() {
        byte[] payload = bytes(0x68, 0x02, 0x11, 0x02, 0x00, 0x01, 0x00, 0x14);
        assertArrayEquals(COMMAND_SET_AMBIENT, SonyFrame.encode(SonyFrame.TYPE_COMMAND, 0, payload));
    }

    @Test
    public void checksumWrapsModulo256() {
        byte[] payload = bytes(0xF0, 0x20);
        assertArrayEquals(hex("3E 0C 01 00 00 00 02 F0 20 1F 3C"),
                SonyFrame.encode(SonyFrame.TYPE_COMMAND, 1, payload));
    }

    @Test
    public void escapingRoundTripKeepsMarkerBytes() {
        byte[] payload = bytes(0x3C, 0x3D, 0x3E, 0x00);
        byte[] frame = SonyFrame.encode(SonyFrame.TYPE_COMMAND, 0, payload);
        assertArrayEquals(hex("3E 0C 00 00 00 00 04 3D 2C 3D 2D 3D 2E 00 C7 3C"), frame);

        SonyFrame.Decoder decoder = new SonyFrame.Decoder();
        List<byte[]> frames = decoder.feed(frame, frame.length);
        assertEquals(1, frames.size());
        assertArrayEquals(payload, SonyFrame.payloadOf(frames.get(0)));
        assertEquals(0x0C, frames.get(0)[0] & 0xFF);
        assertEquals(0x00, frames.get(0)[1] & 0xFF);
    }

    @Test
    public void checksumByteIsEscapedWhenItMatchesAMarker() {
        byte[] payload = bytes(0x2F);
        byte[] frame = SonyFrame.encode(SonyFrame.TYPE_COMMAND, 0, payload);
        assertArrayEquals(hex("3E 0C 00 00 00 00 01 2F 3D 2C 3C"), frame);

        SonyFrame.Decoder decoder = new SonyFrame.Decoder();
        List<byte[]> frames = decoder.feed(frame, frame.length);
        assertEquals(1, frames.size());
        assertArrayEquals(payload, SonyFrame.payloadOf(frames.get(0)));
        assertEquals(0x3C, frames.get(0)[frames.get(0).length - 1] & 0xFF);
    }

    @Test
    public void decoderHandlesSplitFrameAcrossTwoFeeds() {
        SonyFrame.Decoder decoder = new SonyFrame.Decoder();
        assertTrue(decoder.feed(COMMAND_GET, 5).isEmpty());

        byte[] rest = new byte[COMMAND_GET.length - 5];
        System.arraycopy(COMMAND_GET, 5, rest, 0, rest.length);
        List<byte[]> frames = decoder.feed(rest, rest.length);
        assertEquals(1, frames.size());
        assertArrayEquals(bytes(0x66, 0x02), SonyFrame.payloadOf(frames.get(0)));
    }

    @Test
    public void decoderHandlesSplitEscapeSequence() {
        byte[] frame = SonyFrame.encode(SonyFrame.TYPE_COMMAND, 0, bytes(0x3C));
        SonyFrame.Decoder decoder = new SonyFrame.Decoder();

        assertTrue(decoder.feed(frame, 8).isEmpty());
        byte[] rest = new byte[frame.length - 8];
        System.arraycopy(frame, 8, rest, 0, rest.length);
        List<byte[]> frames = decoder.feed(rest, rest.length);
        assertEquals(1, frames.size());
        assertArrayEquals(bytes(0x3C), SonyFrame.payloadOf(frames.get(0)));
    }

    @Test
    public void decoderHandlesTwoFramesInOneFeed() {
        byte[] reply = SonyFrame.encode(SonyFrame.TYPE_COMMAND, 0, bytes(0x67, 0x02, 0x01, 0x02, 0x01, 0x01, 0x00, 0x00));
        byte[] combined = new byte[COMMAND_ACK.length + reply.length];
        System.arraycopy(COMMAND_ACK, 0, combined, 0, COMMAND_ACK.length);
        System.arraycopy(reply, 0, combined, COMMAND_ACK.length, reply.length);

        SonyFrame.Decoder decoder = new SonyFrame.Decoder();
        List<byte[]> frames = decoder.feed(combined, combined.length);
        assertEquals(2, frames.size());
        assertEquals(0x01, frames.get(0)[0] & 0xFF);
        assertEquals(0x01, frames.get(0)[1] & 0xFF);
        assertEquals(7, frames.get(0).length);
        assertArrayEquals(bytes(0x67, 0x02, 0x01, 0x02, 0x01, 0x01, 0x00, 0x00),
                SonyFrame.payloadOf(frames.get(1)));
    }

    @Test
    public void decoderIgnoresBytesBeforeStartMarker() {
        SonyFrame.Decoder decoder = new SonyFrame.Decoder();
        List<byte[]> frames = decoder.feed(bytes(0x00, 0xFF, 0x3E, 0x10), 4);
        assertTrue(frames.isEmpty());

        frames = decoder.feed(COMMAND_INIT, COMMAND_INIT.length);
        assertEquals(1, frames.size());
        assertArrayEquals(bytes(0x00, 0x00), SonyFrame.payloadOf(frames.get(0)));
    }

    @Test
    public void parsesNoiseCancellingState() {
        SonyFrame.State state = SonyFrame.parseState(hex("67 02 01 02 01 01 00 00"));
        assertNotNull(state);
        assertEquals(SonyFrame.MODE_NC, state.mode);
        assertEquals(0x02, state.ncType);
        assertEquals(0x01, state.asmType);
        assertEquals(0, state.ambientLevel);
    }

    @Test
    public void parsesAmbientStateAtTwenty() {
        SonyFrame.State state = SonyFrame.parseState(hex("67 02 01 02 00 01 00 14"));
        assertNotNull(state);
        assertEquals(SonyFrame.MODE_AMBIENT, state.mode);
        assertEquals(0x14, state.ambientLevel);
    }

    @Test
    public void parsesOffStateFromEffect() {
        SonyFrame.State state = SonyFrame.parseState(hex("67 02 00 02 00 01 00 00"));
        assertNotNull(state);
        assertEquals(SonyFrame.MODE_OFF, state.mode);
    }

    @Test
    public void parsesOffStateWhenEffectOnButNothingActive() {
        SonyFrame.State state = SonyFrame.parseState(hex("67 02 01 02 00 01 00 00"));
        assertNotNull(state);
        assertEquals(SonyFrame.MODE_OFF, state.mode);
    }

    @Test
    public void rejectsShortStatePayload() {
        assertNull(SonyFrame.parseState(hex("67 02 01 02")));
        assertNull(SonyFrame.parseState(null));
    }

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }

    private static byte[] hex(String text) {
        String[] parts = text.trim().split("\\s+");
        byte[] out = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = (byte) Integer.parseInt(parts[i], 16);
        }
        return out;
    }
}
