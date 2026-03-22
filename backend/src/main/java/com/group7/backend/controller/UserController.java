package com.group7.backend.controller;

import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import com.group7.backend.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        return userService.getUserById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/mentors")
    public Mentor createMentor(@RequestBody Mentor mentor) {
        return userService.createMentor(mentor);
    }

    @PostMapping("/mentees")
    public Mentee createMentee(@RequestBody Mentee mentee) {
        return userService.createMentee(mentee);
    }

    @GetMapping("/mentors")
    public List<Mentor> getAllMentors() {
        return userService.getAllMentors();
    }

    @GetMapping("/mentees")
    public List<Mentee> getAllMentees() {
        return userService.getAllMentees();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
