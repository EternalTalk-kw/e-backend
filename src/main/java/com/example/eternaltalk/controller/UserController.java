package com.example.eternaltalk.controller;

import com.example.eternaltalk.domain.User;
import com.example.eternaltalk.dto.AuthDtos.*;
import com.example.eternaltalk.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<User> me(){ return ResponseEntity.ok(userService.getMe()); }

    @PutMapping("/profile")
    public ResponseEntity<Void> update(@Valid @RequestBody UpdateProfileRequest req){
        userService.updateProfile(req);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Void> delete(){
        userService.deleteMe();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/consent")
    public ResponseEntity<Void> consent(@RequestBody ConsentRequest req){
        userService.updateConsent(req);
        return ResponseEntity.ok().build();
    }
}
