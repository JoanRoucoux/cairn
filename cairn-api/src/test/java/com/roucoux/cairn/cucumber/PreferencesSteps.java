package com.roucoux.cairn.cucumber;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.generated.model.PreferencesResponse;
import com.roucoux.cairn.generated.model.UpdatePreferencesRequest;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcOperations;

public class PreferencesSteps {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcOperations jdbc;

    @Before
    public void usePatchCapableClient() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @Given("the preferences are reset")
    public void thePreferencesAreReset() {
        jdbc.update("update preferences set language = 'fr', theme = 'system'");
    }

    @When("I set the language to {word}")
    public void iSetTheLanguageTo(String language) {
        UpdatePreferencesRequest request = new UpdatePreferencesRequest();
        request.setLanguage(UpdatePreferencesRequest.LanguageEnum.fromValue(language));
        restTemplate.exchange(
                "/session/preferences", HttpMethod.PATCH, new HttpEntity<>(request), PreferencesResponse.class);
    }

    @Then("the preferences are language {word} and theme {word}")
    public void thePreferencesAreLanguageAndTheme(String language, String theme) {
        PreferencesResponse preferences = restTemplate.getForObject("/session/preferences", PreferencesResponse.class);
        assertThat(preferences.getLanguage().getValue()).isEqualTo(language);
        assertThat(preferences.getTheme().getValue()).isEqualTo(theme);
    }
}
