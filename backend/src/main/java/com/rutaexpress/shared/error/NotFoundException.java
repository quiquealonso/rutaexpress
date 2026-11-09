package com.rutaexpress.shared.error;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String entity, Object id) {
        super(entity + " no encontrado: " + id);
    }
}
