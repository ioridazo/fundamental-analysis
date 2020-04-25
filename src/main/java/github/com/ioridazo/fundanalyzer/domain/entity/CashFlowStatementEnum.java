package github.com.ioridazo.fundanalyzer.domain.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum CashFlowStatementEnum {
    // 何か
    ;

    private final String subject;

    CashFlowStatementEnum(String subject) {
        this.subject = subject;
    }

    @JsonCreator
    public static CashFlowStatementEnum fromValue(String subject) {
        return Arrays.stream(values())
                .filter(v -> v.subject.equals(subject))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(String.valueOf(subject)));
    }

    @JsonValue
    public String toValue() {
        return this.subject;
    }

    @Override
    public String toString() {
        return String.format("CashFlowStatementEnum[code = %s]", this.subject);
    }
}
