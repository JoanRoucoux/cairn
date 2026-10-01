package com.roucoux.cairn.infrastructure.auth;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.security.web.webauthn.api.Bytes;

public final class AttestationFixtures {

    public static final String ICLOUD = "fbfc3007-154e-4ecc-8c0b-6e020557d7bd";
    public static final String GOOGLE = "ea9b8d66-4d01-1d21-3ce4-b6b48cb575d4";
    public static final String YUBIKEY_5_NFC = "fa2b99dc-9e39-4257-8f92-4a30d23c4118";
    public static final String UNKNOWN = "11111111-2222-3333-4444-555555555555";
    public static final String NONE = "00000000-0000-0000-0000-000000000000";

    private AttestationFixtures() {}

    public static Bytes attestationWithAaguid(String aaguid) {
        return new Bytes(cborAttestation(authData(aaguid, true)));
    }

    public static Bytes attestationWithoutCredentialData() {
        return new Bytes(cborAttestation(authData(NONE, false)));
    }

    private static byte[] authData(String aaguid, boolean withCredentialData) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[32]);
        out.write(withCredentialData ? 0x45 : 0x01);
        out.writeBytes(new byte[4]);
        if (withCredentialData) {
            UUID id = UUID.fromString(aaguid);
            out.writeBytes(ByteBuffer.allocate(16)
                    .putLong(id.getMostSignificantBits())
                    .putLong(id.getLeastSignificantBits())
                    .array());
            byte[] credentialId = {1, 2, 3, 4};
            out.write(0);
            out.write(credentialId.length);
            out.writeBytes(credentialId);
            out.writeBytes(coseEs256Key());
        }
        return out.toByteArray();
    }

    private static byte[] coseEs256Key() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {(byte) 0xA5, 0x01, 0x02, 0x03, 0x26, 0x20, 0x01});
        out.writeBytes(new byte[] {0x21, 0x58, 0x20});
        out.writeBytes(filled(32, 0x11));
        out.writeBytes(new byte[] {0x22, 0x58, 0x20});
        out.writeBytes(filled(32, 0x22));
        return out.toByteArray();
    }

    private static byte[] cborAttestation(byte[] authData) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xA3);
        text(out, "fmt");
        text(out, "none");
        text(out, "attStmt");
        out.write(0xA0);
        text(out, "authData");
        out.write(0x58);
        out.write(authData.length);
        out.writeBytes(authData);
        return out.toByteArray();
    }

    private static void text(ByteArrayOutputStream out, String value) {
        out.write(0x60 + value.length());
        out.writeBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] filled(int length, int value) {
        byte[] bytes = new byte[length];
        java.util.Arrays.fill(bytes, (byte) value);
        return bytes;
    }
}
