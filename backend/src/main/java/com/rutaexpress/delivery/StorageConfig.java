package com.rutaexpress.delivery;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Configuration
class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean
    @ConditionalOnProperty(name = "rutaexpress.storage.type", havingValue = "minio")
    FileStorage minioStorage(@Value("${rutaexpress.storage.minio.endpoint}") String endpoint,
                             @Value("${rutaexpress.storage.minio.access-key}") String accessKey,
                             @Value("${rutaexpress.storage.minio.secret-key}") String secretKey,
                             @Value("${rutaexpress.storage.minio.bucket:proofs}") String bucket) {
        MinioClient client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        return new FileStorage() {
            private volatile boolean bucketReady;

            private void ensureBucket() throws Exception {
                if (!bucketReady) {
                    if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                        client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                        log.info("Bucket {} creado en MinIO", bucket);
                    }
                    bucketReady = true;
                }
            }

            @Override
            public void store(String key, InputStream content, long size, String contentType) {
                try {
                    ensureBucket();
                    client.putObject(PutObjectArgs.builder().bucket(bucket).object(key)
                            .stream(content, size, -1).contentType(contentType).build());
                } catch (Exception e) {
                    throw new IllegalStateException("No se pudo guardar el fichero en MinIO", e);
                }
            }

            @Override
            public InputStream load(String key) {
                try {
                    return client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
                } catch (Exception e) {
                    throw new IllegalStateException("No se pudo leer el fichero de MinIO", e);
                }
            }
        };
    }

    @Bean
    @ConditionalOnProperty(name = "rutaexpress.storage.type", havingValue = "local", matchIfMissing = true)
    FileStorage localStorage(@Value("${rutaexpress.storage.local.path:./data/proofs}") String basePath) {
        Path base = Path.of(basePath).toAbsolutePath().normalize();
        return new FileStorage() {
            @Override
            public void store(String key, InputStream content, long size, String contentType) {
                try {
                    Path target = resolve(key);
                    Files.createDirectories(target.getParent());
                    Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public InputStream load(String key) {
                try {
                    return Files.newInputStream(resolve(key));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            private Path resolve(String key) {
                Path target = base.resolve(key).normalize();
                if (!target.startsWith(base)) {
                    throw new IllegalArgumentException("Ruta de fichero no válida");
                }
                return target;
            }
        };
    }
}
