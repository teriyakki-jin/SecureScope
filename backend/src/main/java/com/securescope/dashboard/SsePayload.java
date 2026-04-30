package com.securescope.dashboard;

/**
 * SSE 로 전송되는 메시지 페이로드.
 * type: "EVENT" | "ALERT"
 */
public record SsePayload(String type, Object data) {

    public static SsePayload event(Object data) {
        return new SsePayload("EVENT", data);
    }

    public static SsePayload alert(Object data) {
        return new SsePayload("ALERT", data);
    }
}
