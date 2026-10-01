package org.airbridge.tws;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reassembles 0xAA-framed OPPO/Realme protocol packets from an RFCOMM byte stream.
 * Handles partial frames, fragmented reads, multiple packets in a single read,
 * and discards leading garbage. Correctly decodes LEB128 variable-length packet headers.
 */
public class BudsFramer {

    private byte[] pending = new byte[0];

    public synchronized List<byte[]> append(byte[] buffer, int length) {
        List<byte[]> frames = new ArrayList<>();
        if (length <= 0) return frames;

        // Append incoming bytes to pending buffer
        byte[] next = new byte[pending.length + length];
        System.arraycopy(pending, 0, next, 0, pending.length);
        System.arraycopy(buffer, 0, next, pending.length, length);
        pending = next;

        while (pending.length > 0) {
            // Locate start delimiter 0xAA
            int start = -1;
            for (int i = 0; i < pending.length; i++) {
                if ((pending[i] & 0xFF) == 0xAA) {
                    start = i;
                    break;
                }
            }

            if (start < 0) {
                // No header delimiter found; drop all garbage
                pending = new byte[0];
                break;
            }

            if (start > 0) {
                // Drop leading garbage up to 0xAA
                pending = Arrays.copyOfRange(pending, start, pending.length);
            }

            // Need at least 2 bytes to read the start of length
            if (pending.length < 2) {
                break;
            }

            // Decode LEB128 TotalLen
            int totalLen = 0;
            int lenBytes = 0;
            boolean lengthFound = false;

            for (int i = 1; i < Math.min(pending.length, 5); i++) {
                int b = pending[i] & 0xFF;
                totalLen |= (b & 0x7F) << (7 * (i - 1));
                if ((b & 0x80) == 0) {
                    lenBytes = i;
                    lengthFound = true;
                    break;
                }
            }

            if (!lengthFound) {
                // Length header still incomplete
                break;
            }

            int frameLen = 1 + lenBytes + totalLen;
            if (totalLen < 7 || frameLen > 4096) {
                // Invalid frame length; drop the corrupt 0xAA and resume scan
                pending = Arrays.copyOfRange(pending, 1, pending.length);
                continue;
            }

            if (pending.length < frameLen) {
                // Wait for remainder of frame
                break;
            }

            byte[] fullFrame = Arrays.copyOfRange(pending, 0, frameLen);
            pending = Arrays.copyOfRange(pending, frameLen, pending.length);

            // Normalize frame so downstream parser always receives standard header:
            // [0]=0xAA, [1]=TotalLen low, [2]=0, [3]=0, [4]=Cmd low, [5]=Cmd high, [6]=Seq, [7]=PayLen low, [8]=PayLen high, [9..]=Payload
            byte[] normalized = normalizeFrame(fullFrame, lenBytes, totalLen);
            if (normalized != null) {
                frames.add(normalized);
            }
        }

        return frames;
    }

    private static byte[] normalizeFrame(byte[] raw, int lenBytes, int totalLen) {
        if (lenBytes == 1) {
            return raw;
        }

        // When LEB128 is 2+ bytes, collapse into standard single-byte length representation
        // for payload parser compatibility
        int payLen = totalLen - 7;
        byte[] norm = new byte[9 + payLen];
        norm[0] = (byte) 0xAA;
        norm[1] = (byte) (totalLen & 0xFF);
        norm[2] = 0x00;
        norm[3] = 0x00;

        // Skip the extra LEB128 header bytes to extract Cmd, Seq, PayLen, Payload
        int srcOffset = 1 + lenBytes + 2; // skip AA, lenBytes, and 2 reserved 0x00 bytes
        if (srcOffset + 5 + payLen <= raw.length) {
            System.arraycopy(raw, srcOffset, norm, 4, 5 + payLen);
            return norm;
        }

        return raw;
    }

    public synchronized void reset() {
        pending = new byte[0];
    }
}
