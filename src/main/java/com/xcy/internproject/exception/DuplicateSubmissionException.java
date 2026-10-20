package com.xcy.internproject.exception;

public class DuplicateSubmissionException extends RuntimeException {

    public DuplicateSubmissionException() {
        super("相同工单已在短时间内提交，请勿重复提交");
    }
}
