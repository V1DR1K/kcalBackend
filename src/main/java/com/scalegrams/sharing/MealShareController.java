package com.scalegrams.sharing;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.scalegrams.common.CurrentUser;
import com.scalegrams.sharing.MealShareDtos.AcceptMealShareRequest;
import com.scalegrams.sharing.MealShareDtos.CreateMealShareRequest;
import com.scalegrams.sharing.MealShareDtos.MealShareAcceptanceResponse;
import com.scalegrams.sharing.MealShareDtos.MealShareCreatedResponse;
import com.scalegrams.sharing.MealShareDtos.MealSharePreviewResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/nutrition/meal-shares")
public class MealShareController {
    private final MealShareService service;
    private final CurrentUser currentUser;

    public MealShareController(MealShareService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping
    MealShareCreatedResponse create(Authentication authentication, @Valid @RequestBody CreateMealShareRequest request) {
        return service.create(currentUser.from(authentication), request);
    }

    @GetMapping("/{token}")
    MealSharePreviewResponse preview(Authentication authentication, @PathVariable String token) {
        return service.preview(currentUser.from(authentication), token);
    }

    @PostMapping("/{token}/accept")
    MealShareAcceptanceResponse accept(Authentication authentication, @PathVariable String token,
            @Valid @RequestBody AcceptMealShareRequest request) {
        return service.accept(currentUser.from(authentication), token, request);
    }
}
