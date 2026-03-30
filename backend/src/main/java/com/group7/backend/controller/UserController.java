package com.group7.backend.controller;

import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import com.group7.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Profiles", description = "User, mentor, and mentee profile endpoints")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "List users")
    @ApiResponse(responseCode = "200", description = "List of users",
            content = @Content(schema = @Schema(implementation = User.class)))
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User found",
                    content = @Content(schema = @Schema(implementation = User.class))),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    public ResponseEntity<User> getUserById(@Parameter(description = "User id") @PathVariable Long id) {
        return userService.getUserById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/mentors")
    @Operation(summary = "Create mentor profile")
    @ApiResponse(responseCode = "200", description = "Mentor created",
            content = @Content(schema = @Schema(implementation = Mentor.class)))
    public Mentor createMentor(@RequestBody Mentor mentor) {
        return userService.createMentor(mentor);
    }

    @PostMapping("/mentees")
    @Operation(summary = "Create mentee profile")
    @ApiResponse(responseCode = "200", description = "Mentee created",
            content = @Content(schema = @Schema(implementation = Mentee.class)))
    public Mentee createMentee(@RequestBody Mentee mentee) {
        return userService.createMentee(mentee);
    }

    @GetMapping("/mentors")
    @Operation(summary = "List mentors")
    @ApiResponse(responseCode = "200", description = "List of mentors",
            content = @Content(schema = @Schema(implementation = Mentor.class)))
    public List<Mentor> getAllMentors() {
        return userService.getAllMentors();
    }

    @GetMapping("/mentees")
    @Operation(summary = "List mentees")
    @ApiResponse(responseCode = "200", description = "List of mentees",
            content = @Content(schema = @Schema(implementation = Mentee.class)))
    public List<Mentee> getAllMentees() {
        return userService.getAllMentees();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User deleted"),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    public ResponseEntity<Void> deleteUser(@Parameter(description = "User id") @PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
