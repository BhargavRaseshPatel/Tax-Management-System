package com.propertytax.common;

import java.net.URI;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;

/** One place that decides "real AWS or local emulator" for every AWS SDK client. */
public final class AwsClients {

	private AwsClients() {}

	/**
	 * Empty endpoint: real AWS, credentials from the default chain (the ECS task role when deployed).
	 * Endpoint set: a local emulator. Emulators accept any credentials, so the ones below are placeholders, not
	 * secrets. Real AWS credentials are never configured in code.
	 */
	public static <B extends AwsClientBuilder<B, C>, C> B configure(B builder, String region, String emulatorEndpoint) {
		builder.region(Region.of(region));
		if (!emulatorEndpoint.isBlank()) {
			builder.endpointOverride(URI.create(emulatorEndpoint))
					.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")));
		}
		return builder;
	}

}
