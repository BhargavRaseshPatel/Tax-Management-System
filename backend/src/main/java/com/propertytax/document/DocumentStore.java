package com.propertytax.document;

import java.net.URI;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

/**
 * The only class that talks to Amazon S3. The bucket is private; clients never get a key or a URL, they download
 * through the API so every request is authorized.
 *
 * Deployed: real S3, credentials from the ECS task role (default provider chain), least-privilege policy in
 * infra/storage.yaml. Local: an S3 emulator when S3_ENDPOINT is set.
 */
@Component
public class DocumentStore {

	private final S3Client s3;
	private final String bucket;

	DocumentStore(@Value("${app.documents.bucket}") String bucket, @Value("${app.documents.region}") String region,
			@Value("${app.documents.endpoint:}") String endpoint) {
		this.bucket = bucket;
		var builder = S3Client.builder().region(Region.of(region));
		if (!endpoint.isBlank()) {
			// Local emulator only: it needs path-style URLs and accepts any credentials, so these are placeholders,
			// not secrets. Real AWS credentials are never configured in code.
			builder.endpointOverride(URI.create(endpoint)).forcePathStyle(true)
					.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")));
		}
		this.s3 = builder.build();
	}

	public void put(String key, byte[] content, String contentType) {
		s3.putObject(request -> request.bucket(bucket).key(key).contentType(contentType)
				.serverSideEncryption(ServerSideEncryption.AES256), RequestBody.fromBytes(content));
	}

	/** Empty when the object does not exist. */
	public Optional<byte[]> get(String key) {
		try {
			return Optional.of(s3.getObjectAsBytes(request -> request.bucket(bucket).key(key)).asByteArray());
		} catch (NoSuchKeyException e) {
			return Optional.empty();
		}
	}

}
