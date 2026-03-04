package com.seiri.backup_restore.model;

public class MongoConnectionConfig {
    private String host;
    private int port;
    private String database;
    private String username;
    private String password;
    private String authSource;
    
    public MongoConnectionConfig() {
    }
    
    public MongoConnectionConfig(String host, int port, String database, String username, String password) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.authSource = database; // Default to database name
    }
    
    public MongoConnectionConfig(String host, int port, String database, String username, String password, String authSource) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.authSource = authSource;
    }
    
    // Getters and Setters
    public String getHost() {
        return host;
    }
    
    public void setHost(String host) {
        this.host = host;
    }
    
    public int getPort() {
        return port;
    }
    
    public void setPort(int port) {
        this.port = port;
    }
    
    public String getDatabase() {
        return database;
    }
    
    public void setDatabase(String database) {
        this.database = database;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public String getAuthSource() {
        return authSource;
    }
    
    public void setAuthSource(String authSource) {
        this.authSource = authSource;
    }
}