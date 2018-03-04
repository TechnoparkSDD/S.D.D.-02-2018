package com.colorit.backend.entities;

@SuppressWarnings("unused")
public class UserEntity {
    private Integer id;
    private String nickname;
    private String email;
    private String passwordHash;
    private String avatarPath;

    private GameResults gameResults;

    public UserEntity() {
    }

    public UserEntity(String nickname, String email, String password) {
        this.nickname = nickname;
        this.email = email;
        this.passwordHash = password;
    }

    public UserEntity(String nickname, String password) {
        this.nickname = nickname;
        this.passwordHash = password;
    }

    public Integer getId() {
        return id;
    }

    public void setGameResults(GameResults gameResults) {
        this.gameResults = gameResults;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getNickname() {
        return this.nickname;
    }

    public String getAvatarPath() {
        return avatarPath;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public GameResults getGameResults() {
        return gameResults;
    }

    public Double getRating() {
        if (gameResults.getCountGames() == 0) {
            return 0.0;
        }
        return gameResults.getCountWins() / gameResults.getCountGames().doubleValue();
    }

    public Integer getCountWins() {
        return gameResults.getCountWins();
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setAvatarPath(String avatarPath) {
        this.avatarPath = avatarPath;
    }

    public void setCountGames(Integer countGames) {
        gameResults.setCountGames(countGames);
    }

    public void setCountWins(Integer countWins) {
        this.gameResults.setCountWins(countWins);
    }
}