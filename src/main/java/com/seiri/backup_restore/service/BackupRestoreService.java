package com.seiri.backup_restore.service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.BsonBinaryReader;
import org.bson.BsonBinaryWriter;
import org.bson.codecs.DocumentCodec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.io.BasicOutputBuffer;
import org.bson.io.ByteBufferBsonInput;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class BackupRestoreService {
    
    @Autowired
    private MongoConnectionService connectionService;
    
    /**
     * Backup selected collections to a ZIP file using BSON format
     */
    public boolean backupCollections(List<String> collectionNames, File outputFile) {
        try {
            MongoDatabase database = connectionService.getDatabase();
            if (database == null) {
                throw new IllegalStateException("Not connected to database");
            }
            
            // Create temporary directory for BSON files
            Path tempDir = Files.createTempDirectory("mongo_backup_");
            
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputFile))) {
                
                for (String collectionName : collectionNames) {
                    System.out.println("✓ Backing up collection: " + collectionName);
                    
                    MongoCollection<Document> collection = database.getCollection(collectionName);
                    
                    // Create BSON file for collection
                    File bsonFile = tempDir.resolve(collectionName + ".bson").toFile();
                    
                    // Get codec registry from collection
                    CodecRegistry codecRegistry = collection.getCodecRegistry();
                    DocumentCodec documentCodec = new DocumentCodec(codecRegistry);
                    
                    int documentCount = 0;
                    
                    try (FileOutputStream fos = new FileOutputStream(bsonFile)) {
                        // Write each document as BSON
                        for (Document doc : collection.find()) {
                            // Encode document to BSON
                            BasicOutputBuffer buffer = new BasicOutputBuffer();
                            BsonBinaryWriter writer = new BsonBinaryWriter(buffer);
                            
                            try {
                                documentCodec.encode(writer, doc, EncoderContext.builder().build());
                                
                                // Get BSON data (already contains size prefix)
                                byte[] bsonData = buffer.toByteArray();
                                
                                // Write BSON document to file
                                fos.write(bsonData);
                                
                                documentCount++;
                            } finally {
                                writer.close();
                            }
                        }
                    }
                    
                    System.out.println("  ✓ " + documentCount + " documents backed up");
                    
                    // Add to ZIP (just the filename, no path)
                    addToZip(bsonFile, collectionName + ".bson", zos);
                    bsonFile.delete();
                }
            }
            
            // Clean up temp directory
            Files.deleteIfExists(tempDir);
            
            System.out.println("✓ Backup completed successfully!");
            return true;
            
        } catch (Exception e) {
            System.err.println("✗ Backup failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Get list of collections available in backup ZIP file
     */
    public List<String> getCollectionsInBackup(File zipFile) throws Exception {
        List<String> collections = new ArrayList<>();
        
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                
                if (!entry.isDirectory() && entry.getName().endsWith(".bson")) {
                    // Extract just the filename without path and extension
                    String fullName = entry.getName();
                    String fileName = new File(fullName).getName(); // Remove any path
                    String collectionName = fileName.replace(".bson", "");
                    collections.add(collectionName);
                }
            }
        }
        
        return collections;
    }
    
    /**
     * Restore selected collections from ZIP file
     * @param zipFile Backup file
     * @param collectionsToRestore List of collection names to restore (null = restore all)
     * @param dropMode true = drop existing collections, false = replace matching documents
     */
    public boolean restoreCollections(File zipFile, List<String> collectionsToRestore, boolean dropMode) {
        try {
            MongoDatabase database = connectionService.getDatabase();
            if (database == null) {
                throw new IllegalStateException("Not connected to database");
            }
            
            System.out.println("✓ Starting restore from: " + zipFile.getName());
            System.out.println("✓ Mode: " + (dropMode ? "Drop & Restore" : "Replace/Update"));
            
            int restoredCount = 0;
            
            // Extract ZIP
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
                ZipEntry entry;
                
                while ((entry = zis.getNextEntry()) != null) {
                    if (!entry.isDirectory() && entry.getName().endsWith(".bson")) {
                        // Extract just the filename without path
                        String fullName = entry.getName();
                        String fileName = new File(fullName).getName();
                        String collectionName = fileName.replace(".bson", "");
                        
                        // Check if this collection should be restored
                        if (collectionsToRestore != null && !collectionsToRestore.contains(collectionName)) {
                            System.out.println("  ⊘ Skipping collection: " + collectionName);
                            zis.closeEntry();
                            continue;
                        }
                        
                        System.out.println("  ⟳ Restoring collection: " + collectionName);
                        
                        // Read BSON content
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            baos.write(buffer, 0, len);
                        }
                        
                        byte[] bsonData = baos.toByteArray();
                        
                        // Parse BSON documents
                        List<Document> documents = parseBsonDocuments(bsonData, database);
                        
                        System.out.println("    ✓ Parsed " + documents.size() + " documents");
                        
                        MongoCollection<Document> collection = database.getCollection(collectionName);
                        
                        if (dropMode) {
                            // Drop and recreate
                            System.out.println("    ⟳ Dropping existing collection");
                            collection.drop();
                            collection = database.getCollection(collectionName);
                            
                            // Insert all documents
                            if (documents != null && !documents.isEmpty()) {
                                System.out.println("    ⟳ Inserting " + documents.size() + " documents");
                                
                                // Insert in batches to avoid memory issues
                                int batchSize = 1000;
                                for (int i = 0; i < documents.size(); i += batchSize) {
                                    int end = Math.min(i + batchSize, documents.size());
                                    collection.insertMany(documents.subList(i, end));
                                }
                            }
                        } else {
                            // Replace mode - update or insert
                            if (documents != null && !documents.isEmpty()) {
                                System.out.println("    ⟳ Replacing/Updating " + documents.size() + " documents");
                                for (Document doc : documents) {
                                    Object id = doc.get("_id");
                                    if (id != null) {
                                        // Replace if exists, insert if not
                                        collection.replaceOne(
                                            new Document("_id", id),
                                            doc,
                                            new com.mongodb.client.model.ReplaceOptions().upsert(true)
                                        );
                                    } else {
                                        collection.insertOne(doc);
                                    }
                                }
                            }
                        }
                        
                        System.out.println("    ✓ Collection restored successfully");
                        restoredCount++;
                    }
                    zis.closeEntry();
                }
            }
            
            System.out.println("✓ Restore completed! " + restoredCount + " collections restored.");
            return true;
            
        } catch (Exception e) {
            System.err.println("✗ Restore failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Parse BSON documents from byte array
     */
    private List<Document> parseBsonDocuments(byte[] bsonData, MongoDatabase database) {
        List<Document> documents = new ArrayList<>();
        
        if (bsonData == null || bsonData.length == 0) {
            return documents;
        }
        
        try {
            // Get codec registry
            CodecRegistry codecRegistry = database.getCodecRegistry();
            DocumentCodec documentCodec = new DocumentCodec(codecRegistry);
            
            int offset = 0;
            
            while (offset < bsonData.length) {
                try {
                    // BSON documents start with a 4-byte little-endian size field
                    if (offset + 4 > bsonData.length) {
                        break;
                    }
                    
                    // Read document size (little-endian)
                    int documentSize = ByteBuffer.wrap(bsonData, offset, 4)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .getInt();
                    
                    // Validate document size
                    if (documentSize <= 4 || documentSize > 16 * 1024 * 1024) {
                        break;
                    }
                    
                    if (offset + documentSize > bsonData.length) {
                        break;
                    }
                    
                    // Extract document bytes
                    byte[] docBytes = new byte[documentSize];
                    System.arraycopy(bsonData, offset, docBytes, 0, documentSize);
                    
                    // Create BSON input and reader
                    ByteBufferBsonInput bsonInput = new ByteBufferBsonInput(
                        new ByteBufNIO(ByteBuffer.wrap(docBytes))
                    );
                    BsonBinaryReader reader = new BsonBinaryReader(bsonInput);
                    
                    try {
                        // Decode document
                        Document doc = documentCodec.decode(reader, DecoderContext.builder().build());
                        documents.add(doc);
                    } finally {
                        reader.close();
                    }
                    
                    // Move to next document
                    offset += documentSize;
                    
                } catch (Exception e) {
                    System.err.println("Failed to parse document at offset " + offset);
                    break;
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error parsing BSON documents: " + e.getMessage());
            e.printStackTrace();
        }
        
        return documents;
    }
    
    /**
     * Add file to ZIP
     */
    private void addToZip(File file, String zipEntryName, ZipOutputStream zos) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            ZipEntry zipEntry = new ZipEntry(zipEntryName);
            zos.putNextEntry(zipEntry);
            
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, len);
            }
            
            zos.closeEntry();
        }
    }
    
    /**
     * Complete ByteBuf implementation for BSON reading
     */
    private static class ByteBufNIO implements org.bson.ByteBuf {
        private final ByteBuffer buffer;
        
        public ByteBufNIO(ByteBuffer buffer) {
            this.buffer = buffer;
        }
        
        @Override
        public int capacity() {
            return buffer.capacity();
        }
        
        @Override
        public org.bson.ByteBuf put(int index, byte b) {
            buffer.put(index, b);
            return this;
        }
        
        @Override
        public int remaining() {
            return buffer.remaining();
        }
        
        @Override
        public org.bson.ByteBuf put(byte[] src, int offset, int length) {
            buffer.put(src, offset, length);
            return this;
        }
        
        @Override
        public boolean hasRemaining() {
            return buffer.hasRemaining();
        }
        
        @Override
        public org.bson.ByteBuf put(byte b) {
            buffer.put(b);
            return this;
        }
        
        @Override
        public org.bson.ByteBuf flip() {
            buffer.flip();
            return this;
        }
        
        @Override
        public byte[] array() {
            return buffer.array();
        }
        
        @Override
        public int limit() {
            return buffer.limit();
        }
        
        @Override
        public org.bson.ByteBuf position(int newPosition) {
            buffer.position(newPosition);
            return this;
        }
        
        @Override
        public org.bson.ByteBuf clear() {
            buffer.clear();
            return this;
        }
        
        @Override
        public org.bson.ByteBuf order(java.nio.ByteOrder byteOrder) {
            buffer.order(byteOrder);
            return this;
        }
        
        @Override
        public byte get() {
            return buffer.get();
        }
        
        @Override
        public byte get(int index) {
            return buffer.get(index);
        }
        
        @Override
        public org.bson.ByteBuf get(byte[] bytes) {
            buffer.get(bytes);
            return this;
        }
        
        @Override
        public org.bson.ByteBuf get(int index, byte[] bytes) {
            int pos = buffer.position();
            buffer.position(index);
            buffer.get(bytes);
            buffer.position(pos);
            return this;
        }
        
        @Override
        public org.bson.ByteBuf get(byte[] bytes, int offset, int length) {
            buffer.get(bytes, offset, length);
            return this;
        }
        
        @Override
        public org.bson.ByteBuf get(int index, byte[] bytes, int offset, int length) {
            int pos = buffer.position();
            buffer.position(index);
            buffer.get(bytes, offset, length);
            buffer.position(pos);
            return this;
        }
        
        @Override
        public long getLong() {
            return buffer.getLong();
        }
        
        @Override
        public long getLong(int index) {
            return buffer.getLong(index);
        }
        
        @Override
        public double getDouble() {
            return buffer.getDouble();
        }
        
        @Override
        public double getDouble(int index) {
            return buffer.getDouble(index);
        }
        
        @Override
        public int getInt() {
            return buffer.getInt();
        }
        
        @Override
        public int getInt(int index) {
            return buffer.getInt(index);
        }
        
        @Override
        public int position() {
            return buffer.position();
        }
        
        @Override
        public org.bson.ByteBuf limit(int newLimit) {
            buffer.limit(newLimit);
            return this;
        }
        
        @Override
        public org.bson.ByteBuf asReadOnly() {
            return new ByteBufNIO(buffer.asReadOnlyBuffer());
        }
        
        @Override
        public org.bson.ByteBuf duplicate() {
            return new ByteBufNIO(buffer.duplicate());
        }
        
        @Override
        public java.nio.ByteBuffer asNIO() {
            return buffer;
        }
        
        @Override
        public int getReferenceCount() {
            return 1;
        }
        
        @Override
        public org.bson.ByteBuf retain() {
            return this;
        }
        
        @Override
        public void release() {
            // No-op for NIO buffer
        }

        // These methods may not exist in older BSON versions
        // Remove @Override if you get compilation errors
        public org.bson.ByteBuf putLong(int index, long value) {
            buffer.putLong(index, value);
            return this;
        }
        
        public org.bson.ByteBuf putDouble(int index, double value) {
            buffer.putDouble(index, value);
            return this;
        }
    }
}