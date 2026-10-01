package com.roucoux.cairn.infrastructure.auth;

import static java.util.Map.entry;

import com.webauthn4j.converter.AttestationObjectConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.web.webauthn.api.Bytes;

public final class PasskeyProviders {

    private static final AttestationObjectConverter CONVERTER = new AttestationObjectConverter(new ObjectConverter());

    private static final Map<UUID, String> BY_AAGUID = Map.ofEntries(
            entry(UUID.fromString("fbfc3007-154e-4ecc-8c0b-6e020557d7bd"), "iCloud"),
            entry(UUID.fromString("dd4ec289-e01d-41c9-bb89-70fa845d4bf2"), "iCloud"),
            entry(UUID.fromString("ea9b8d66-4d01-1d21-3ce4-b6b48cb575d4"), "Google Password Manager"),
            entry(UUID.fromString("08987058-cadc-4b81-b6e1-30de50dcbe96"), "Windows Hello"),
            entry(UUID.fromString("6028b017-b1d4-4c02-b4b3-afcdafc96bb2"), "Windows Hello"),
            entry(UUID.fromString("9ddd1817-af5a-4672-a2b9-3e3dd95000a9"), "Windows Hello"),
            entry(UUID.fromString("bada5566-a7aa-401f-bd96-45619a55120d"), "1Password"),
            entry(UUID.fromString("d548826e-79b4-db40-a3d8-11116f7e8349"), "Bitwarden"),
            entry(UUID.fromString("19083c3d-8383-4b18-bc03-8f1c9ab2fd1b"), "Clé de sécurité"),
            entry(UUID.fromString("cb69481e-8ff7-4039-93ec-0a2729a154a8"), "Clé de sécurité"),
            entry(UUID.fromString("ee882879-721c-4913-9775-3dfcce97072a"), "Clé de sécurité"),
            entry(UUID.fromString("ff4dac45-ede8-4ec2-aced-cf66103f4335"), "Clé de sécurité"),
            entry(UUID.fromString("0a357157-9b18-4c8a-920e-d156e972b2f8"), "Clé de sécurité"),
            entry(UUID.fromString("24673149-6c86-42e7-98d9-433fb5b73296"), "Clé de sécurité"),
            entry(UUID.fromString("a02167b9-ae71-4ac7-9a07-06432ebb6f1c"), "Clé de sécurité"),
            entry(UUID.fromString("c5ef55ff-ad9a-4b9f-b580-adebafe026d0"), "Clé de sécurité"),
            entry(UUID.fromString("03012cb7-4fb2-42e7-9e8d-a81f10e2a5e9"), "Clé de sécurité"),
            entry(UUID.fromString("2fc0579f-8113-47ea-b116-bb5a8db9202a"), "Clé de sécurité"),
            entry(UUID.fromString("a25342c0-3cdc-4414-8e46-f4807fca511c"), "Clé de sécurité"),
            entry(UUID.fromString("d7781e5d-e353-46aa-afe2-3ca49f13332a"), "Clé de sécurité"),
            entry(UUID.fromString("fa2b99dc-9e39-4257-8f92-4a30d23c4118"), "Clé de sécurité"),
            entry(UUID.fromString("f4ce5fc0-57d3-46f5-a736-efb7d5bc63b5"), "Clé de sécurité"),
            entry(UUID.fromString("7dab85a5-d16d-4eaf-a7ef-4c1385b151c5"), "Clé de sécurité"),
            entry(UUID.fromString("9eb7eabc-9db5-49a1-b6c3-555a802093f4"), "Clé de sécurité"));

    private PasskeyProviders() {}

    public static String providerOf(Bytes attestationObject) {
        if (attestationObject == null) {
            return null;
        }
        try {
            var credentialData = CONVERTER
                    .convert(attestationObject.getBytes())
                    .getAuthenticatorData()
                    .getAttestedCredentialData();
            return credentialData == null
                    ? null
                    : BY_AAGUID.get(credentialData.getAaguid().getValue());
        } catch (RuntimeException unreadable) {
            return null;
        }
    }
}
