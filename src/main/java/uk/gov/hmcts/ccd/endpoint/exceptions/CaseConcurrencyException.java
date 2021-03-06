package uk.gov.hmcts.ccd.endpoint.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.CONFLICT)
public class CaseConcurrencyException extends ApiException {

    public CaseConcurrencyException(String message) {
        super(message);
    }
}
