package com.colorit.backend.common;

import com.colorit.backend.services.responses.UserServiceResponse;
import com.colorit.backend.views.output.ResponseView;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotNull;
import java.util.Locale;

@Component
public class UserResponseMaker extends AbstractResponseMaker {
    public UserResponseMaker(@NotNull MessageSource messageSource) {
        super(messageSource);
    }

    @Override
    public ResponseEntity<ResponseView> makeResponse(UserServiceResponse userServiceResponse, Locale locale,
                                                     String responseFieldName) {
        if (userServiceResponse.isValid()) {
            return makeOkUserServiceResponse(userServiceResponse, responseFieldName);
        } else {
            return makeErrorUserServiceResponse(userServiceResponse, getMessageSource().getMessage(
                    userServiceResponse.getStatus().getAlternativeMessage(), null, locale));
        }
    }
}