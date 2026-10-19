package com.nnson128.transfer.exception;

public class AccountServiceUnavailableException extends RuntimeException {
    public AccountServiceUnavailableException() {
        super("Account service unavailable");
    }
}
