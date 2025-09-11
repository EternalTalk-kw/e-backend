package com.example.eternaltalk.service;

import com.example.eternaltalk.domain.User;
import com.example.eternaltalk.dto.AuthDtos.*;
import com.example.eternaltalk.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    private User me() {
        String email = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userRepository.findByEmail(email).orElseThrow();
    }

    public User getMe(){ return me(); }

    public void updateProfile(UpdateProfileRequest req){
        User u = me();
        u.setNickname(req.nickname());
        userRepository.save(u);
    }

    public void deleteMe(){
        User u = me();
        userRepository.delete(u);
    }

    public void updateConsent(ConsentRequest req){
        User u = me();
        u.setConsent(req.consent());
        userRepository.save(u);
    }
}
