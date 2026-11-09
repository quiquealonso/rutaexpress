package com.rutaexpress.delivery;

import java.io.InputStream;

/** Almacenamiento de fotos y firmas: MinIO (compatible S3) en Docker, disco local en desarrollo. */
public interface FileStorage {

    void store(String key, InputStream content, long size, String contentType);

    InputStream load(String key);
}
