package com.securescope.audit;

import com.securescope.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping("/verify")
    public ApiResponse<AuditService.VerifyResult> verify() {
        return ApiResponse.ok(auditService.verify());
    }
}
