package com.example.demo.dto.response;

public class AuthResponseDTO {

    private String token;
    private UserDTO user;

    public AuthResponseDTO(String token, UserDTO user) {
        this.token = token;
        this.user = user;
    }

    public String getToken() { return token; }
    public UserDTO getUser() { return user; }

    public static class UserDTO {
        private Long id;
        private String username;
        private String email;
        private String role;
        private Long githubId;
        private String avatarUrl;

        public UserDTO(Long id, String username, String email, String role, Long githubId, String avatarUrl) {
            this.id = id;
            this.username = username;
            this.email = email;
            this.role = role;
            this.githubId = githubId;
            this.avatarUrl = avatarUrl;
        }

        public Long getId() { return id; }
        public String getUsername() { return username; }
        public String getEmail() { return email; }
        public String getRole() { return role; }
        public Long getGithubId() { return githubId; }
        public String getAvatarUrl() { return avatarUrl; }
    }
}
