package fr.inria.diverse.Utils;

import com.github.luben.zstd.ZstdOutputStream;

import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarStyle;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FolderJsonlCompressor {
    private static final Logger logger = LogManager.getLogger(FolderJsonlCompressor.class);

    /**
     * Merges all `.jsonl` files in the specified folder, compresses them into a
     * `.zst` file,
     * and optionally deletes the original files after processing.
     *
     * @param folderPath      The path to the folder containing `.jsonl` files.
     * @param outputFile      The path to the output compressed `.zst` file.
     * @param deleteOriginals Whether to delete the original `.jsonl` files after
     *                        processing.
     * @throws IOException If any file operations fail.
     */
    public static void compressAndDeleteJsonlFiles(String folderPath, String outputFile, boolean deleteOriginals)
            throws IOException {
        // List all .jsonl files in the folder
        List<Path> jsonlFiles = Files.walk(Paths.get(folderPath))
                .filter(Files::isRegularFile)
                .collect(Collectors.toList());

        if (jsonlFiles.isEmpty()) {
            System.out.println("No files found in the folder.");
            return;
        }

        try (OutputStream fileOut = Files.newOutputStream(Paths.get(outputFile));
                ZstdOutputStream zstdOut = new ZstdOutputStream(fileOut);
                ProgressBar progressBar = Progress.infoBar("Compressing", logger, jsonlFiles.size())) {
            // Progress Bar
            for (Path file : jsonlFiles) {
                progressBar.step(); // Update progress bar

                System.out.println("Processing: " + file);

                // Read and compress the .jsonl file
                try (BufferedReader reader = Files.newBufferedReader(file)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        zstdOut.write((line + System.lineSeparator()).getBytes());
                    }
                }

                // Optionally delete the original file
                if (deleteOriginals) {
                    Files.delete(file);
                    System.out.println("Deleted: " + file);
                }
            }
        }

        System.out.println("Successfully compressed all .jsonl files to: " + outputFile);
    }

}