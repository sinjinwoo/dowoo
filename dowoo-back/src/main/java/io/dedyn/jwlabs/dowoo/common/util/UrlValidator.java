package io.dedyn.jwlabs.dowoo.common.util;

import io.dedyn.jwlabs.dowoo.common.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.net.URI;

public final class UrlValidator {

    private UrlValidator() {
    }

    public static void requireHttpUrl(String url) {
        try {
            URI uri = new URI(url);
            if (uri.getScheme() == null || !(uri.getScheme().equals("http") || uri.getScheme().equals("https"))) {
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "올바른 URL 형식이 아닙니다.");
        }
    }
}
