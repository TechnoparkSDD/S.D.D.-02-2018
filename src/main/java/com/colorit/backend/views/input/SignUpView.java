package com.colorit.backend.views.input;

import com.colorit.backend.entities.db.UserEntity;
import com.colorit.backend.views.ViewStatus;
import com.colorit.backend.views.ViewStatusCode;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SignUpView extends AbstractInputView {
    private final String nickname;
    private final String email;
    private final String password;
    private final String passwordCheck;

    @SuppressWarnings("unused")
    @JsonCreator
    SignUpView(@JsonProperty("nickname") String nickname,
               @JsonProperty("email") String email,
               @JsonProperty("password") String password,
               @JsonProperty("passwordCheck") String passwordCheck) {
        this.nickname = nickname;
        this.email = email;
        this.password = password;
        this.passwordCheck = passwordCheck;
    }

    @Override
    public ViewStatus checkValid() {
        final ViewStatus viewStatus = new ViewStatus();
        if (nickname == null || nickname.isEmpty()) {
            viewStatus.addStatusCode(ViewStatusCode.EMPTY_NICKNAME);
        }

        checkEmail(viewStatus, email);

        boolean passwordFilled = true;
        if (password == null || password.isEmpty()) {
            passwordFilled = false;
            viewStatus.addStatusCode(ViewStatusCode.EMPTY_PASSWORD);
        }

        if (passwordCheck == null || passwordCheck.isEmpty()) {
            passwordFilled = false;
            viewStatus.addStatusCode(ViewStatusCode.EMPTY_PASSWORD_CHECK);
        }

        if (passwordFilled && !passwordCheck.equals(password)) {
            viewStatus.addStatusCode(ViewStatusCode.PASSWORD_NOT_MATCH_STATE);
        }

        return viewStatus;
    }

    @Override
    public String getNickname() {
        return this.nickname;
    }

    @Override
    public String getEmail() {
        return this.email;
    }

    @Override
    public String getPassword() {
        return this.password;
    }

    public UserEntity toEntity() {
        return new UserEntity(nickname, email, password);
    }
}