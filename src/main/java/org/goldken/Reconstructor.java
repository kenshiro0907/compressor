package org.goldken;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cette classe est responsable de la reconstruction des fichiers à partir de leurs chunks stockés.
 * Elle interagit avec un système de stockage de chunks pour récupérer les données nécessaires
 * et reconstruire les fichiers dans un répertoire de sortie spécifié.
 */
public class Reconstructor {
    private static final Logger logger = LoggerFactory.getLogger(Reconstructor.class);
    private final ChunkStorage deduplicationSystem;
    private final String outputDir;

    /**
     * Constructeur pour initialiser un Reconstructor.
     *
     * @param deduplicationSystem Le système de stockage de chunks utilisé pour la reconstruction.
     * @param outputDir           Le répertoire de sortie où les fichiers reconstruits seront enregistrés.
     */
    public Reconstructor(ChunkStorage deduplicationSystem, String outputDir) {
        this.deduplicationSystem = deduplicationSystem;
        this.outputDir = outputDir;
        try {
            ensureOutputDirectoryExists();
        } catch (IOException e) {
            logger.error("Erreur lors de la creation du repertoire de sortie : {}", outputDir, e);
            throw new FileReconstructionException("Erreur lors de la creation du repertoire de sortie", e);
        }
    }

    /**
     * Crée le répertoire de sortie s'il n'existe pas déjà.
     * Si le répertoire ne peut pas être créé, une exception est levée.
     */
    private void ensureOutputDirectoryExists() throws IOException {
        try {
            Files.createDirectories(Paths.get(outputDir));
        } catch (IOException e) {
            logger.error("Erreur lors de la creation du repertoire de sortie : {}", outputDir, e);
            throw new FileReconstructionException("Erreur lors de la creation du repertoire de sortie", e);
        }
    }

    /**
     * Récupère les chunks associés à un fichier spécifique à partir de la base de données.
     *
     * @param conn     La connexion à la base de données.
     * @param filename Le nom du fichier pour lequel récupérer les chunks.
     * @return Une liste de {@link ChunkMetadata} contenant les informations sur les chunks.
     * @throws SQLException Si une erreur survient lors de l'exécution de la requête SQL.
     */
    private List<ChunkMetadata> retrieveChunksForFile(Connection conn, String filename) throws SQLException {
        List<ChunkMetadata> chunks = new ArrayList<>();
        String query = """
            SELECT fc.chunk_number, c.chunk_hash, c.file_path
            FROM file_chunks fc
            JOIN chunks c ON fc.chunk_hash = c.chunk_hash
            WHERE REGEXP_REPLACE(fc.filename, '_chunk_\\d+$', '') = ?
            ORDER BY fc.chunk_number
        """;

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, filename);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                chunks.add(new ChunkMetadata(
                    rs.getString("chunk_hash"),
                    rs.getString("file_path"),
                    rs.getInt("chunk_number")
                ));
            }
        }

        if (chunks.isEmpty()) {
            logger.warn("Aucun chunk trouve pour le fichier : {}", filename);
        }

        return chunks;
    }

    /**
     * Reconstruit un fichier à partir de ses chunks stockés.
     * Les chunks sont récupérés à partir de la base de données et écrits dans un fichier de sortie.
     *
     * @param filename Le nom du fichier à reconstruire.
     * @throws FileReconstructionException Si une erreur survient lors de la reconstruction.
     */
    public void rebuildFileFromChunks(String filename) {
        try (Connection connect = deduplicationSystem.getConnection()) {
            long startTime = System.nanoTime();
            List<ChunkMetadata> chunks = retrieveChunksForFile(connect, filename);
            if (chunks.isEmpty()) {
                throw new FileReconstructionException("Aucun chunk trouve pour le fichier : " + filename);
            }

            Path outputPath = Paths.get(outputDir, filename);
            try (OutputStream outputStream = Files.newOutputStream(outputPath)) {
                for (ChunkMetadata chunk : chunks) {
                    Path chunkPath = Paths.get(chunk.storagePath);

                    if (!Files.exists(chunkPath)) {
                        throw new FileReconstructionException("Chunk introuvable : " + chunkPath);
                    }

                    byte[] chunkData = Files.readAllBytes(chunkPath);
                    outputStream.write(chunkData);
                }
            }

            long endTime = System.nanoTime();
            logger.info("Fichier reconstruit avec succes en {}ms : {}", (endTime - startTime) / 1_000_000, outputPath);

        } catch (SQLException | IOException e) {
            logger.error("Erreur lors de la reconstruction du fichier : {}", filename, e);
            throw new FileReconstructionException("Erreur lors de la reconstruction du fichier : " + filename, e);
        }
    }

    /**
     * Record pour stocker les informations d'un chunk.
     *
     * @param hash        Le hash du chunk.
     * @param storagePath Le chemin de stockage du chunk.
     * @param number      Le numéro du chunk dans le fichier.
     */
    private record ChunkMetadata(String hash, String storagePath, int number) {}

    /**
     * Exception personnalisée pour les erreurs de reconstruction.
     */
    public static class FileReconstructionException extends RuntimeException {
        /**
         * Constructeur pour une exception avec un message et une cause.
         *
         * @param message Le message d'erreur.
         * @param cause   La cause de l'exception.
         */
        public FileReconstructionException(String message, Throwable cause) {
            super(message, cause);
        }

        /**
         * Constructeur pour une exception avec un message.
         *
         * @param message Le message d'erreur.
         */
        public FileReconstructionException(String message) {
            super(message);
        }
    }
}