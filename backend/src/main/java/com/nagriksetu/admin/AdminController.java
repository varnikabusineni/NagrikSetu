package com.nagriksetu.admin;

import com.nagriksetu.common.PageResponse;
import com.nagriksetu.security.CurrentUser;
import com.nagriksetu.user.RoleCode;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Administrator-only API (restricted to ROLE_ADMIN in SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService service;

    public AdminController(AdminService service) {
        this.service = service;
    }

    @GetMapping("/accounts")
    public List<AdminDtos.AccountDto> accounts(@RequestParam(required = false) RoleCode role,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(defaultValue = "false") boolean includeCitizens) {
        return service.list(role, q, includeCitizens);
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminDtos.AccountDto create(@Valid @RequestBody AdminDtos.CreateOfficial req) {
        return service.createOfficial(CurrentUser.require(), req);
    }

    @PutMapping("/accounts/{id}")
    public AdminDtos.AccountDto update(@PathVariable Long id, @Valid @RequestBody AdminDtos.UpdateAccount req) {
        return service.update(CurrentUser.require(), id, req);
    }

    @PostMapping("/accounts/{id}/status")
    public AdminDtos.AccountDto status(@PathVariable Long id, @Valid @RequestBody AdminDtos.StatusRequest req) {
        return service.setEnabled(CurrentUser.require(), id, req.enabled());
    }

    @PostMapping("/accounts/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable Long id, @Valid @RequestBody AdminDtos.ResetPassword req) {
        service.resetPassword(CurrentUser.require(), id, req.newPassword());
    }

    @GetMapping("/audit")
    public PageResponse<AdminDtos.AuditDto> audit(@RequestParam(required = false) String action,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "50") int size) {
        return service.auditLog(action, page, size);
    }
}
