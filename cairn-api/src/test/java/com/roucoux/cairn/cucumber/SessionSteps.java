package com.roucoux.cairn.cucumber;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.generated.model.PasskeyResponse;
import com.roucoux.cairn.generated.model.SessionResponse;
import com.roucoux.cairn.infrastructure.auth.AttestationFixtures;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;

public class SessionSteps {

    private static final String ANONYMOUS_OWNER = "anonymousUser";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcOperations jdbc;

    @Autowired
    private PublicKeyCredentialUserEntityRepository userEntities;

    @Autowired
    private UserCredentialRepository credentials;

    private SessionResponse session;

    @Before
    public void resetPasskeys() {
        jdbc.update("delete from user_credentials");
        jdbc.update("delete from user_entities");
    }

    @Given("a registered passkey {string} from an iCloud authenticator")
    public void aRegisteredPasskeyFromAnICloudAuthenticator(String label) {
        register(label, AttestationFixtures.ICLOUD);
    }

    @Given("a registered passkey {string} from an authenticator the API does not know")
    public void aRegisteredPasskeyFromAnUnknownAuthenticator(String label) {
        register(label, AttestationFixtures.UNKNOWN);
    }

    @When("I read the session")
    public void iReadTheSession() {
        session = restTemplate.getForObject("/session", SessionResponse.class);
    }

    @Then("the passkey {string} is provided by {string}")
    public void thePasskeyIsProvidedBy(String label, String provider) {
        assertThat(passkey(label).getProvider()).hasToString(provider);
    }

    @Then("the passkey {string} has no provider")
    public void thePasskeyHasNoProvider(String label) {
        assertThat(passkey(label).getProvider()).isNull();
    }

    @Then("no passkey is the current one")
    public void noPasskeyIsTheCurrentOne() {
        assertThat(session.getPasskeys())
                .extracting(PasskeyResponse::getCurrent)
                .containsOnly(false);
    }

    private void register(String label, String aaguid) {
        PublicKeyCredentialUserEntity owner = userEntities.findByUsername(ANONYMOUS_OWNER);
        if (owner == null) {
            owner = ImmutablePublicKeyCredentialUserEntity.builder()
                    .name(ANONYMOUS_OWNER)
                    .id(Bytes.random())
                    .displayName("Joan Roucoux")
                    .build();
            userEntities.save(owner);
        }
        credentials.save(ImmutableCredentialRecord.builder()
                .credentialId(Bytes.random())
                .userEntityUserId(owner.getId())
                .publicKey(new ImmutablePublicKeyCose(new byte[] {1}))
                .label(label)
                .created(Instant.parse("2026-01-01T00:00:00Z"))
                .attestationObject(AttestationFixtures.attestationWithAaguid(aaguid))
                .build());
    }

    private PasskeyResponse passkey(String label) {
        return session.getPasskeys().stream()
                .filter(passkey -> label.equals(passkey.getLabel()))
                .findFirst()
                .orElseThrow();
    }
}
