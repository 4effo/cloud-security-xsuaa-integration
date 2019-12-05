package com.sap.cloud.security.token.validation.validators;

import com.sap.cloud.security.config.OAuth2ServiceConfiguration;
import com.sap.cloud.security.token.Token;
import com.sap.cloud.security.token.validation.Validator;
import com.sap.cloud.security.xsuaa.client.*;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static com.sap.cloud.security.config.cf.CFConstants.XSUAA.APP_ID;
import static com.sap.cloud.security.config.Service.XSUAA;

/**
 * Class used to build a token validator. Custom validators can be added via
 * {@link #with(Validator)} method.
 */
public class JwtValidatorBuilder {
	private final Collection<Validator<Token>> validators = new ArrayList<>();
	private OAuth2ServiceConfiguration configuration;
	private OidcConfigurationService oidcConfigurationService = null;
	private OAuth2TokenKeyService tokenKeyService = null;
	private OAuth2ServiceConfiguration otherConfiguration;
	private Validator<Token> audienceValidator;

	private JwtValidatorBuilder() {
		// use getInstance factory method
	}

	public static JwtValidatorBuilder getInstance(OAuth2ServiceConfiguration configuration) {
		JwtValidatorBuilder tokenBuilder = new JwtValidatorBuilder();
		tokenBuilder.configuration = configuration;
		return tokenBuilder;
	}

	/**
	 * Add the validator to the validation chain.
	 *
	 * @param validator
	 *            the validator used for validation.
	 * @return this builder.
	 */
	public JwtValidatorBuilder with(Validator<Token> validator) {
		validators.add(validator);
		return this;
	}

	public JwtValidatorBuilder withAudienceValidator(Validator<Token> audienceValidator) {
		this.audienceValidator = audienceValidator;
		return this;
	}

	public JwtValidatorBuilder withOAuth2TokenKeyService(OAuth2TokenKeyService tokenKeyService) {
		this.tokenKeyService = tokenKeyService;
		return this;
	}

	public JwtValidatorBuilder withOidcConfigurationService(OidcConfigurationService oidcConfigurationService) {
		this.oidcConfigurationService = oidcConfigurationService;
		return this;
	}

	public JwtValidatorBuilder configureAnotherServiceInstance(
			@Nullable OAuth2ServiceConfiguration otherConfiguration) {
		this.otherConfiguration = otherConfiguration;
		return this;
	}

	/**
	 * @return the combined validators.
	 */
	public CombiningValidator<Token> build() {
		List<Validator<Token>> allValidators = createDefaultValidators();
		allValidators.addAll(validators);
		return new CombiningValidator<>(allValidators);
	}

	private List<Validator<Token>> createDefaultValidators() {
		List<Validator<Token>> defaultValidators = new ArrayList<>();
		defaultValidators.add(new JwtTimestampValidator());
		if (configuration != null && configuration.getService().equals(XSUAA)) {
			XsuaaJwtAudienceValidator audienceValidator = new XsuaaJwtAudienceValidator(
					configuration.getProperty(APP_ID), configuration.getClientId());
			if (otherConfiguration != null) {
				audienceValidator.configureAnotherServiceInstance(otherConfiguration.getProperty(APP_ID),
						otherConfiguration.getClientId());
			}
			defaultValidators.add(new XsuaaJwtIssuerValidator(configuration.getDomain()));
			defaultValidators.add(
					new JwtSignatureValidator(getTokenKeyServiceWithCache(), getOidcConfigurationServiceWithCache()));
			defaultValidators.add(getAudienceValidator(configuration));
		}
		return defaultValidators;
	}

	private OAuth2TokenKeyServiceWithCache getTokenKeyServiceWithCache() {
		return OAuth2TokenKeyServiceWithCache.getInstance()
				.withTokenKeyService(tokenKeyService);
	}

	private OidcConfigurationServiceWithCache getOidcConfigurationServiceWithCache() {
		return OidcConfigurationServiceWithCache.getInstance()
				.withOidcConfigurationService(oidcConfigurationService);
	}

	private Validator<Token> getAudienceValidator(OAuth2ServiceConfiguration configuration) {
		return Optional.ofNullable(audienceValidator)
				.orElse(new XsuaaJwtAudienceValidator(configuration.getProperty(APP_ID), configuration.getClientId()));
	}

}
