package com.seiri.backup_restore.service;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.seiri.backup_restore.model.MongoConnectionConfig;
import org.bson.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class MongoConnectionService {
    
    private MongoClient mongoClient;
    private MongoDatabase database;
    
    /**
     * Test MongoDB connection
     */
    public boolean testConnection(MongoConnectionConfig config) {
        try {
            MongoClient testClient = createMongoClient(config);
            testClient.listDatabaseNames().first();
            testClient.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Connect to MongoDB with auto-create support
     */
    public boolean connect(MongoConnectionConfig config) {
        return connect(config, false);
    }
    
    /**
     * Connect to MongoDB
     * @param config Connection configuration
     * @param createIfNotExists If true, will try to create database/user if authentication fails
     */
    public boolean connect(MongoConnectionConfig config, boolean createIfNotExists) {
        try {
            closeConnection();
            
            // First, try to connect with provided credentials
            try {
                mongoClient = createMongoClient(config);
                database = mongoClient.getDatabase(config.getDatabase());
                
                // Test connection by trying to get collection names
                database.listCollectionNames().first();
                
                System.out.println("Successfully connected to database: " + config.getDatabase());
                return true;
                
            } catch (com.mongodb.MongoSecurityException authException) {
                // Authentication failed
                System.err.println("Authentication failed: " + authException.getMessage());
                
                if (createIfNotExists && config.getUsername() != null && !config.getUsername().isEmpty()) {
                    System.out.println("Attempting to connect without authentication to create database/user...");
                    
                    // Try to connect without authentication
                    try {
                        MongoConnectionConfig noAuthConfig = new MongoConnectionConfig(
                            config.getHost(),
                            config.getPort(),
                            config.getDatabase(),
                            null,  // No username
                            null   // No password
                        );
                        
                        MongoClient tempClient = createMongoClient(noAuthConfig);
                        MongoDatabase adminDb = tempClient.getDatabase("admin");
                        
                        // Test if we can connect without auth
                        adminDb.listCollectionNames().first();
                        
                        System.out.println("Connected without authentication. Creating user...");
                        
                        // Create user for the target database
                        MongoDatabase targetDb = tempClient.getDatabase(config.getDatabase());
                        
                        Document createUserCommand = new Document("createUser", config.getUsername())
                            .append("pwd", config.getPassword())
                            .append("roles", Collections.singletonList(
                                new Document("role", "dbOwner")
                                    .append("db", config.getDatabase())
                            ));
                        
                        try {
                            targetDb.runCommand(createUserCommand);
                            System.out.println("User created successfully: " + config.getUsername());
                        } catch (Exception e) {
                            System.err.println("Failed to create user: " + e.getMessage());
                        }
                        
                        tempClient.close();
                        
                        // Now try to connect with the newly created user
                        mongoClient = createMongoClient(config);
                        database = mongoClient.getDatabase(config.getDatabase());
                        database.listCollectionNames().first();
                        
                        System.out.println("Successfully connected with new user!");
                        return true;
                        
                    } catch (Exception noAuthException) {
                        System.err.println("Cannot connect without authentication: " + noAuthException.getMessage());
                        throw new RuntimeException(
                            "Authentication failed and cannot create user. Please either:\n" +
                            "1. Create the database and user manually in MongoDB\n" +
                            "2. Enable authentication-free access temporarily\n" +
                            "3. Leave username/password empty to connect without authentication",
                            authException
                        );
                    }
                } else {
                    throw new RuntimeException("Authentication failed: " + authException.getMessage(), authException);
                }
            }
            
        } catch (Exception e) {
            closeConnection();
            throw new RuntimeException("Failed to connect to MongoDB: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get list of collection names from connected database
     */
    public List<String> getCollectionNames() {
        if (database == null) {
            throw new IllegalStateException("Not connected to database");
        }
        
        List<String> collections = new ArrayList<>();
        database.listCollectionNames().forEach(collections::add);
        return collections;
    }
    
    /**
     * Get MongoDatabase instance
     */
    public MongoDatabase getDatabase() {
        return database;
    }
    
    /**
     * Close current connection
     */
    public void closeConnection() {
        if (mongoClient != null) {
            try {
                mongoClient.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mongoClient = null;
            database = null;
        }
    }
    
    /**
     * Create MongoClient with authentication
     */
    private MongoClient createMongoClient(MongoConnectionConfig config) {
        try {
            if (config.getUsername() != null && !config.getUsername().trim().isEmpty()) {
                // With authentication using MongoCredential
                String authDb = (config.getAuthSource() != null && !config.getAuthSource().isEmpty()) 
                    ? config.getAuthSource() 
                    : config.getDatabase();
                
                MongoCredential credential = MongoCredential.createCredential(
                    config.getUsername(),
                    authDb,
                    config.getPassword().toCharArray()
                );
                
                MongoClientSettings settings = MongoClientSettings.builder()
                    .applyToClusterSettings(builder ->
                        builder.hosts(Collections.singletonList(
                            new ServerAddress(config.getHost(), config.getPort())
                        ))
                    )
                    .credential(credential)
                    .build();
                
                System.out.println("Connecting with authentication: " + config.getUsername() + "@" + authDb);
                
                return MongoClients.create(settings);
                
            } else {
                // Without authentication
                String connectionString = String.format(
                    "mongodb://%s:%d/%s",
                    config.getHost(),
                    config.getPort(),
                    config.getDatabase()
                );
                
                System.out.println("Connecting without authentication: " + connectionString);
                
                MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(connectionString))
                    .build();
                
                return MongoClients.create(settings);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create MongoDB client: " + e.getMessage(), e);
        }
    }
}