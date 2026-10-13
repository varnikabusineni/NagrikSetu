package com.nagriksetu.dashboard;

import com.nagriksetu.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/citizen")
    public DashboardService.CitizenDashboard citizen() {
        return service.citizen(CurrentUser.require());
    }

    @GetMapping("/community")
    public DashboardService.CommunityStats community() {
        CurrentUser.require();
        return service.community();
    }
}
