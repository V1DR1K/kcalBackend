package com.scalegrams.nutrition;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.scalegrams.common.CurrentUser;

@RestController
@RequestMapping("/api/admin/foods")
public class FoodAdminController {
    private final CookedYieldBackfillService cookedYieldBackfill;
    private final CurrentUser currentUser;

    public FoodAdminController(CookedYieldBackfillService cookedYieldBackfill, CurrentUser currentUser) {
        this.cookedYieldBackfill = cookedYieldBackfill;
        this.currentUser = currentUser;
    }

    @PostMapping("/cooked-yield-backfill")
    CookedYieldBackfillService.CookedYieldBackfillReport backfill(Authentication authentication,
            @RequestParam(defaultValue = "true") boolean dryRun,
            @RequestParam(defaultValue = "25") int limit) {
        return cookedYieldBackfill.backfill(currentUser.from(authentication), dryRun, limit);
    }
}
