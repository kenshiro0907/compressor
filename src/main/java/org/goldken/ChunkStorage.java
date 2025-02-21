package org.goldken;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

import static org.goldken.Utils.computeSha256Hash;
import org.goldken.models.ChunkData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Gère le stockage et la déduplication des chunks de fichiers.
 */
public class ChunkStorage {
    private static final Logger logger = LoggerFactory.getLogger(ChunkStorage.class);
    protected  static final String STORAGE_BASE_PATH = "E:/Projets/Github/compressor/files-storage";

    private final HikariDataSource dataSource;

    /**
     * Initialise une nouvelle instance de ChunkStorage.
     */
    public ChunkStorage() {
        this.dataSource = configureHikariDataSource();
        initializeDatabase();
        resetDatabase();
        cleanAndPrepareStorageDirectory();
    }

    /**
     * Configure la source de données HikariCP.
     *
     * @return Une instance de HikariDataSource.
     */
    private HikariDataSource configureHikariDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/compressor");
        config.setUsername("postgres");
        config.setPassword("securepassword");
        config.setMaximumPoolSize(10);
        return new HikariDataSource(config);
    }

    /**
     * Retourne une connexion à la base de données.
     *
     * @return Une connexion SQL.
     * @throws SQLException Si une erreur survient lors de l'obtention de la connexion.
     */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Initialise la base de données en créant les tables nécessaires.
     */
    private void initializeDatabase() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS chunks (
                    chunk_hash VARCHAR(64) PRIMARY KEY,
                    file_path TEXT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS file_chunks (
                    id SERIAL PRIMARY KEY,
                    filename TEXT NOT NULL,
                    chunk_hash VARCHAR(64) REFERENCES chunks(chunk_hash),
                    chunk_number INT NOT NULL,
                    UNIQUE(filename, chunk_number)
                )
            """);
        } catch (SQLException e) {
            logger.error("Erreur d'initialisation de la base de donnees", e);
            throw new DatabaseException("Erreur d'initialisation de la base de donnees", e);
        }
    }

    /**
     * Réinitialise la base de données en supprimant toutes les données.
     */
    private void resetDatabase() {
        try (Connection conn = dataSource.getConnection();
            Statement stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE file_chunks CASCADE");
            stmt.execute("TRUNCATE TABLE chunks CASCADE");
        } catch (SQLException e) {
            logger.error("Erreur lors de la reinitialisation de la base de donnees", e);
            throw new DatabaseException("Erreur lors de la reinitialisation de la base de donnees", e);
        }
    }

    /**
     * Génère le chemin de stockage pour un chunk.
     *
     * @param hash Le hash du chunk.
     * @return Le chemin de stockage.
     */
    protected String buildChunkStoragePath(String hash) {
        return Paths.get(STORAGE_BASE_PATH, hash).toString();
    }

    /**
     * Initialise le dossier de stockage en le nettoyant s'il existe déjà.
     */
    private void cleanAndPrepareStorageDirectory() {
        try {
            Path storagePath = Paths.get(STORAGE_BASE_PATH);
            if (Files.exists(storagePath)) {
                Files.walk(storagePath)
                        .sorted((a, b) -> b.compareTo(a))
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                logger.error("Erreur lors de la suppression de {}", path, e);
                            }
                        });
            }
            Files.createDirectories(storagePath);
        } catch (IOException e) {
            logger.error("Erreur lors de l'initialisation du stockage", e);
            throw new StorageException("Erreur lors de l'initialisation du stockage", e);
        }
    }

    /**
     * Sauvegarde un chunk dans le système de fichiers.
     *
     * @param chunk       Le chunk à sauvegarder.
     * @param storagePath Le chemin de stockage.
     * @throws IOException Si une erreur survient lors de la sauvegarde.
     */
    protected void storeChunkToFileSystem(byte[] chunk, String storagePath) throws IOException {
        Path path = Paths.get(storagePath);
        Files.createDirectories(path.getParent());
        Files.write(path, chunk);
    }

    /**
     * Lit tous les chunks dans le système de fichiers.
     *
     * @param storagePath Le chemin de stockage.
     * @throws IOException Si une erreur survient lors de la lecture.
     */
    protected  byte[] loadChunkFromFileSystem(String storagePath) throws IOException {
        return Files.readAllBytes(Paths.get(storagePath));
    }

    /**
     * Ajoute un chunk au système de stockage.
     *
     * @param chunk       Le chunk à ajouter.
     * @param filename    Le nom du fichier d'origine.
     * @param chunkNumber Le numéro du chunk dans le fichier.
     * @return Un objet ChunkData représentant le chunk ajouté.
     * @throws NoSuchAlgorithmException Si l'algorithme de hachage n'est pas disponible.
     * @throws DatabaseException        Si une erreur survient lors de l'ajout en base de données.
     * @throws StorageException         Si une erreur survient lors de la sauvegarde physique.
     */
    public ChunkData storeAndRegisterChunk(byte[] chunk, String filename, int chunkNumber) throws NoSuchAlgorithmException, Utils.HashException, IOException {
        Objects.requireNonNull(chunk, "Le chunk ne peut pas être null");
        Objects.requireNonNull(filename, "Le nom du fichier ne peut pas être null");

        String hash = computeSha256Hash(chunk);
        String storagePath = buildChunkStoragePath(hash);

        try (Connection connect = dataSource.getConnection()) {
            connect.setAutoCommit(false);
            try {
                boolean chunkExists = false;
                try (PreparedStatement checkStmt = connect.prepareStatement(
                        "SELECT 1 FROM chunks WHERE chunk_hash = ?")) {
                    checkStmt.setString(1, hash);
                    ResultSet rs = checkStmt.executeQuery();
                    chunkExists = rs.next();
                }

                if (!chunkExists) {
                    try (PreparedStatement insertChunkStmt = connect.prepareStatement(
                            "INSERT INTO chunks (chunk_hash, file_path) VALUES (?, ?)")) {
                        insertChunkStmt.setString(1, hash);
                        insertChunkStmt.setString(2, storagePath);
                        insertChunkStmt.executeUpdate();

                        try {
                            storeChunkToFileSystem(chunk, storagePath);
                        } catch (IOException e) {
                            throw new SQLException("Erreur lors de la sauvegarde physique du chunk", e);
                        }
                    }
                }

                try (PreparedStatement insertFileChunkStmt = connect.prepareStatement(
                        "INSERT INTO file_chunks (filename, chunk_hash, chunk_number) VALUES (?, ?, ?)")) {
                    insertFileChunkStmt.setString(1, filename);
                    insertFileChunkStmt.setString(2, hash);
                    insertFileChunkStmt.setInt(3, chunkNumber);
                    insertFileChunkStmt.executeUpdate();
                }

                connect.commit();
                return new ChunkData(hash, chunk.length, storagePath);
            } catch (SQLException e) {
                connect.rollback();
                throw new DatabaseException("Erreur lors de l'ajout du chunk en base de donnees", e);
            } finally {
                connect.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Erreur lors de l'ajout du chunk", e);
        }
    }

    /**
     * Exception personnalisée pour les erreurs de base de données.
     */
    public static class DatabaseException extends RuntimeException {
        public DatabaseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception personnalisée pour les erreurs de stockage.
     */
    public static class StorageException extends RuntimeException {
        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}