package com.propertytax.document;

import java.util.Optional;

import com.propertytax.common.AwsClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
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

	DocumentStore(@Value("${app.documents.bucket}") String bucket, @Value("${app.aws-region}") String region,
			@Value("${app.documents.endpoint:}") String endpoint) {
		this.bucket = bucket;
		this.s3 = AwsClients.configure(S3Client.builder(), region, endpoint)
				.forcePathStyle(!endpoint.isBlank()) // the emulator has no per-bucket hostnames
				.build();
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
