package com.immutable.credentials.storage;

import com.immutable.credentials.core.Blockchain;
import com.immutable.credentials.model.Block;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import com.immutable.credentials.util.JsonSerializer;

/**
 * Handles persistent storage of blockchain data using JSONL format (one JSON object per line).
 * Provides functionality to save, load, and backup blockchain state.
 * 
 * JSONL format enables efficient append operations without rewriting the entire chain.
 */
public class BlockchainStorage {

    /**
     * Saves the entire blockchain to disk in JSONL format (one block per line).
     *
     * @param blockchain the blockchain to save
     * @param fileName the name of the file to save to
     * @throws IOException if an I/O error occurs during save
     * @throws IllegalArgumentException if blockchain or fileName is null
     */
    public void saveChain(Blockchain blockchain, String fileName) throws IOException {
        if (blockchain == null) {
            throw new IllegalArgumentException("Blockchain cannot be null");
        }
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("File name cannot be null or empty");
        }

        Path dataDir = Paths.get("data");
        if (!Files.exists(dataDir)) {
            Files.createDirectories(dataDir);
        }

        Path filePath = Paths.get("data", fileName);
        
        ArrayList<Block> chain = blockchain.getChain();
        StringBuilder jsonLines = new StringBuilder();
        
        for (Block block : chain) {
            jsonLines.append(JsonSerializer.blockToJson(block)).append("\n");
        }
        
        Files.write(filePath, jsonLines.toString().getBytes());
    }
    /**
     * Loads a blockchain from disk (JSONL format - one block per line).
     *
     * @param fileName the name of the file to load from
     * @return the loaded blockchain
     * @throws IOException if an I/O error occurs during load or file not found
     * @throws IllegalArgumentException if fileName is null or empty
     */
    public Blockchain loadChain(String fileName) throws IOException {
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("File name cannot be null or empty");
        }
        
        Path path = Paths.get("data", fileName);
        
        if (!Files.exists(path)) {
            throw new IOException("File does not exist: " + path);
        }
        
        List<String> lines = Files.readAllLines(path);
        
        if (lines.isEmpty()) {
            throw new IOException("File is empty: " + path);
        }
        
        ArrayList<Block> blocks = new ArrayList<>();
        
        for (String line : lines) {
            if (line != null && !line.trim().isEmpty()) {
                Block block = JsonSerializer.jsonToBlock(line);
                blocks.add(block);
            }
        }
        
        return new Blockchain(blocks);
    }

    /**
     * Saves a single block to disk in append mode (JSONL format).
     * Appends the block as a new line without rewriting the entire chain.
     *
     * @param block the block to save
     * @param fileName the name of the file to append to
     * @throws IOException if an I/O error occurs during save
     * @throws IllegalArgumentException if block or fileName is null
     */
    public void saveBlock(Block block, String fileName) throws IOException {
        if (block == null) {
            throw new IllegalArgumentException("Block cannot be null");
        }
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("File name cannot be null or empty");
        }

        Path dataDir = Paths.get("data");
        if (!Files.exists(dataDir)) {
            Files.createDirectories(dataDir);
        }

        Path filePath = Paths.get("data", fileName);
        
        String blockJson = JsonSerializer.blockToJson(block) + "\n";
        
        if (Files.exists(filePath)) {
            Files.write(filePath, blockJson.getBytes(), StandardOpenOption.APPEND);
        } else {
            Files.write(filePath, blockJson.getBytes());
        }
    }

    /**
     * Creates a backup copy of the blockchain file.
     *
     * @param sourceFileName the source file to backup
     * @param backupFileName the destination backup file name
     * @throws IOException if an I/O error occurs during backup or source not found
     * @throws IllegalArgumentException if sourceFileName or backupFileName is null or empty
     */
    public void createBackup(String sourceFileName, String backupFileName) throws IOException {
        if (sourceFileName == null || sourceFileName.trim().isEmpty()) {
            throw new IllegalArgumentException("Source file name cannot be null or empty");
        }
        if (backupFileName == null || backupFileName.trim().isEmpty()) {
            throw new IllegalArgumentException("Backup file name cannot be null or empty");
        }
        
        Path sourcePath = Paths.get("data", sourceFileName);
        Path backupPath = Paths.get("data", backupFileName);
        
        if (!Files.exists(sourcePath)) {
            throw new IOException("Source file does not exist: " + sourcePath);
        }
        
        Files.copy(sourcePath, backupPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Deletes a blockchain file from storage.
     *
     * @param fileName the name of the file to delete
     * @throws IOException if an I/O error occurs during deletion
     * @throws IllegalArgumentException if fileName is null or empty
     */
    public void deleteChain(String fileName) throws IOException {
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("File name cannot be null or empty");
        }
        
        Path filePath = Paths.get("data", fileName);
        
        if (!Files.exists(filePath)) {
            throw new IOException("File does not exist: " + filePath);
        }
        
        Files.delete(filePath);
    }

    /**
     * Checks if a blockchain file exists in storage.
     *
     * @param fileName the name of the file to check
     * @return true if the file exists, false otherwise
     */
    public boolean chainExists(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return false;
        }
        
        Path filePath = Paths.get("data", fileName);
        return Files.exists(filePath);
    }
}