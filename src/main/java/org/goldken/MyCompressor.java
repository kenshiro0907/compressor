package org.goldken;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Classe principale pour gérer la compression et la déduplication de fichiers.
 * Elle utilise {@code CompressChunkStorage} pour le stockage des chunks et {@code Reconstructor}
 * pour la reconstruction des fichiers.
 */
public class MyCompressor {
    private static final CDC chunker = new CDC();

    /**
     * Méthode principale exécutant le processus de compression et de reconstruction.
     *
     * @param args Arguments de la ligne de commande (non utilisés).
     */
    public static void main(String[] args) {
        ChunkStorage chunkStorageSystem = new ChunkStorage();
        Reconstructor reconstructor = new Reconstructor(chunkStorageSystem, "files-reconstructed");

        try {
            String folderPath = "files-initial";
            processAndChunkFolderFiles(chunkStorageSystem, folderPath);

            String fileToReconstruct = "my_pokedex.png";
            System.out.println("\nReconstruction du fichier : " + fileToReconstruct);
            try {
                reconstructor.rebuildFileFromChunks(fileToReconstruct);
            } catch (Reconstructor.FileReconstructionException e) {
                System.err.println("Échec de la reconstruction : " + e.getMessage());
            }

        } catch (Exception e) {
            System.err.println("Erreur inattendue : " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Parcourt un dossier et traite chaque fichier en le découpant en chunks
     * pour le stockage compressé.
     *
     * @param chunkStorageSystem Le système de stockage des chunks.
     * @param folderPath         Le chemin du dossier contenant les fichiers à traiter.
     */
    private static void processAndChunkFolderFiles(ChunkStorage chunkStorageSystem, String folderPath) {
        try {
            Files.walk(Paths.get(folderPath))
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    try {
                        splitFileIntoChunksAndStore(chunkStorageSystem, file);
                    } catch (Utils.HashException e) {
                        System.err.println("Erreur lors du traitement du fichier " + file + " : " + e.getMessage());
                    }
                });
        } catch (IOException e) {
            System.err.println("Erreur lors du parcours du dossier : " + e.getMessage());
        }
    }

    /**
     * Découpe un fichier en chunks et les stocke dans le système de stockage.
     *
     * @param chunkStorageSystem Le système de stockage des chunks.
     * @param filePath           Le chemin du fichier à traiter.
     * @throws Utils.HashException En cas d'erreur de hachage lors du traitement des chunks.
     */
    private static void splitFileIntoChunksAndStore(ChunkStorage chunkStorageSystem, Path filePath) throws Utils.HashException {
        try {
            List<byte[]> chunks = chunker.chunkFile(String.valueOf(filePath));
            String fileName = filePath.getFileName().toString();

            for (int i = 0; i < chunks.size(); i++) {
                chunkStorageSystem.storeAndRegisterChunk(chunks.get(i), fileName, i);
            }
        } catch (IOException e) {
            System.err.println("Erreur lors du traitement du fichier " + filePath + " : " + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Erreur de hachage : ", e);
        }
    }
}
