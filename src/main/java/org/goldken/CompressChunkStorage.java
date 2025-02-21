package org.goldken;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Classe permettant de gérer le stockage de chunks compressés.
 * Elle hérite de {@code ChunkStorage} et utilise un {@code Compressor}
 * pour la compression et la décompression des chunks.
 */
public class CompressChunkStorage extends ChunkStorage {
    private final Compressor compressor;

    /**
     * Constructeur par défaut initialisant le compresseur.
     */
    public CompressChunkStorage() {
        super();
        this.compressor = new Compressor();
    }

    /**
     * Sauvegarde un chunk compressé dans le stockage.
     *
     * @param chunk       Les données du chunk à compresser et stocker.
     * @param storagePath Le chemin du fichier de stockage.
     * @throws IOException En cas d'erreur d'écriture dans le fichier.
     */
    @Override
    protected void storeChunkToFileSystem(byte[] chunk, String storagePath) throws IOException {
        // Compresser le chunk avec ses métadonnées
        byte[] compressedData = compressor.buildCompressedChunkWithMetadata(chunk);

        // Sauvegarder le chunk compressé
        Path path = Paths.get(storagePath);
        Files.createDirectories(path.getParent());
        Files.write(path, compressedData);
    }

    /**
     * Lit et décompresse un chunk à partir du stockage.
     *
     * @param storagePath Le chemin du fichier où est stocké le chunk compressé.
     * @return Les données du chunk décompressé.
     * @throws IOException En cas d'erreur de lecture du fichier.
     */
    @Override
    protected byte[] loadChunkFromFileSystem(String storagePath) throws IOException {
        byte[] compressedData = Files.readAllBytes(Paths.get(storagePath));
        return compressor.extractAndDecompressChunkWithMetadata(compressedData);
    }
}
