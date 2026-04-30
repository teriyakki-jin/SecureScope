package com.securescope.event;

import com.securescope.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SecurityEventResponse> create(@Valid @RequestBody CreateEventRequest req) {
        return ApiResponse.ok(eventService.ingest(req));
    }

    @GetMapping("/events")
    public ApiResponse<java.util.List<SecurityEventResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<SecurityEventResponse> result = eventService.findAll(page, size);
        return ApiResponse.ok(result.getContent(),
                result.getTotalElements(), page, size);
    }

    @GetMapping("/stats/ip")
    public ApiResponse<Map<String, Long>> ipStats() {
        return ApiResponse.ok(eventService.ipStats());
    }
}
