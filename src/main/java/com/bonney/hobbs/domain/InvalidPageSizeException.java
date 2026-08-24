package com.bonney.hobbs.domain;

public class InvalidPageSizeException extends RuntimeException {

    public InvalidPageSizeException(int requested, int max) {
        super("pageSize=" + requested + " exceeds the maximum of " + max);
    }
}
