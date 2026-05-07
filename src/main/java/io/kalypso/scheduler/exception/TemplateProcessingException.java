package io.kalypso.scheduler.exception;

/**
 * Thrown when Freemarker fails to process a manifest template.
 *
 * <p>Wraps Freemarker's checked exceptions so callers deal with a single
 * unchecked type, consistent with the operator's error-handling convention.
 *
 * <p>Corresponds to error propagation in the Go operator's
 * {@code scheduler/templater.go} {@code ProcessTemplate} method.
 */
public class TemplateProcessingException extends RuntimeException {

    /**
     * @param message description of which template failed and why
     * @param cause   the underlying Freemarker or IO exception
     */
    public TemplateProcessingException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @param message description of the failure (no underlying cause)
     */
    public TemplateProcessingException(String message) {
        super(message);
    }
}
