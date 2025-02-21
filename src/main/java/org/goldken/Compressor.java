package org.goldken;

import java.nio.ByteBuffer;

import com.github.luben.zstd.Zstd;

/**
 * Classe gérant la compression et la décompression de chunks en utilisant l'algorithme Zstandard (Zstd).
 */
public class Compressor {
    private static final int COMPRESSION_LEVEL = 1;

    /**
     * Comprime un chunk de données en utilisant Zstandard.
     *
     * @param chunk Les données à compresser.
     * @return Un tableau d'octets contenant les données compressées, ou le chunk d'origine si la compression n'est pas efficace.
     * @throws IllegalArgumentException Si le chunk est null ou vide.
     */
    public byte[] compressDataWithZstd(byte[] chunk) {
        if (chunk == null || chunk.length == 0) {
            throw new IllegalArgumentException("Le chunk ne peut pas être null ou vide.");
        }

        long maxCompressedSize = Zstd.compressBound(chunk.length);
        if (maxCompressedSize > Integer.MAX_VALUE) {
            throw new IllegalStateException("Taille compressée estimée trop grande pour être allouée en mémoire.");
        }

        byte[] compressedData = new byte[(int) maxCompressedSize];
        long compressedSize = Zstd.compress(compressedData, chunk, COMPRESSION_LEVEL);

        if (Zstd.isError(compressedSize)) {
            throw new RuntimeException("Échec de la compression: " + Zstd.getErrorName(compressedSize));
        }

        if (compressedSize >= chunk.length) {
            return chunk;
        }

        byte[] result = new byte[(int) compressedSize];
        System.arraycopy(compressedData, 0, result, 0, (int) compressedSize);
        return result;
    }

    /**
     * Décompresse un chunk de données compressé avec Zstandard.
     *
     * @param compressedChunk Les données compressées.
     * @param originalSize La taille originale du chunk (si connue, sinon mettre -1).
     * @return Le tableau d'octets contenant les données décompressées.
     * @throws IllegalArgumentException Si le chunk compressé est null ou vide.
     * @throws RuntimeException Si la décompression échoue.
     */
    public byte[] decompressDataWithZstd(byte[] compressedChunk, long originalSize) {
        if (originalSize < 0) {
            originalSize = Zstd.decompress(compressedChunk, new byte[0]);
        }

        if (originalSize == 0) {
            throw new RuntimeException("Impossible de déterminer la taille originale du chunk");
        }

        byte[] result = new byte[(int) originalSize];
        long decompressedSize = Zstd.decompress(result, compressedChunk);

        if (decompressedSize < 0) {
            throw new RuntimeException("Échec de la décompression: " + Zstd.getErrorName(decompressedSize));
        }

        return result;
    }

    /**
     * Crée un chunk compressé contenant aussi des métadonnées (taille originale des données).
     *
     * @param chunk Le chunk de données à compresser.
     * @return Un tableau d'octets contenant la taille originale suivie des données compressées.
     */
    public byte[] buildCompressedChunkWithMetadata(byte[] chunk) {
        byte[] compressedData = compressDataWithZstd(chunk);
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES + compressedData.length);
        buffer.putLong(chunk.length);
        buffer.put(compressedData);
        return buffer.array();
    }

    /**
     * Décompresse un chunk contenant des métadonnées.
     *
     * @param data Les données compressées contenant la taille originale.
     * @return Un tableau d'octets contenant les données décompressées.
     * @throws IllegalArgumentException Si les données sont nulles ou trop courtes.
     */
    public byte[] extractAndDecompressChunkWithMetadata(byte[] data) {
        if (data == null || data.length <= Long.BYTES) {
            throw new IllegalArgumentException("Données invalides : elles doivent contenir la taille originale suivie des données compressées.");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);
        long originalSize = buffer.getLong();
        byte[] compressedData = new byte[data.length - Long.BYTES];
        buffer.get(compressedData);

        return decompressDataWithZstd(compressedData, originalSize);
    }
}
