package com.securescope.detection;

import com.securescope.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    public ApiResponse<List<DetectionAlertResponse>> list(
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String alertType,
            @RequestParam(required = false) String sourceIp,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<DetectionAlertResponse> result =
                alertService.findAll(severity, alertType, sourceIp, page, size);
        return ApiResponse.ok(result.getContent(),
                result.getTotalElements(), page, size);
    }
}
