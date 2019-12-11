package com.citigroup.core.tools;

public class AskResult {

    private final boolean isSuccess;
    private final String failure;
    private final String hostname;

    private AskResult(String hostname, boolean isSuccess, String failure) {
        this.isSuccess = isSuccess;
        this.failure = failure;
        this.hostname = hostname;
    }

    public static AskResult success(String hostname) {
        return new AskResult(hostname, true, "");
    }

    public static AskResult failed(String hostname, String reason) {
        return new AskResult(hostname, false, reason);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb
                .append(isSuccess ? "[SUCCESS]" : "[FAILED]")
                .append('\t')
                .append(hostname);
        if (!isSuccess) {
            sb.append(":\t").append(failure);
        }
        return sb.toString();
    }
}
