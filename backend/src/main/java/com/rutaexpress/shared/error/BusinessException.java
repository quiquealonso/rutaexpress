package com.rutaexpress.shared.error;

/** Violación de una regla de negocio (estado inválido, duplicado...). Se traduce a 409 Conflict. */
public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
