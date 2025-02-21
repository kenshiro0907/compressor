package org.goldken;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.rabinfingerprint.fingerprint.RabinFingerprintLong;
import org.rabinfingerprint.polynomial.Polynomial;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implémente un algorithme de Content-Defined Chunking (CDC) pour diviser un fichier en chunks.
 */
public class CDC {
    private static final Logger logger = LoggerFactory.getLogger(CDC.class);

    // Polynôme optimisé pour Rabin Fingerprinting
    private static final Polynomial POLYNOMIAL = Polynomial.createFromLong(0x3DA3358B4DC173L);

    // Seuil de coupure (4 Ko)
    private static final long MASK = (1 << 12) - 1;

    // Taille maximale d'un chunk (64 Ko)
    private static final int MAX_CHUNK_SIZE = 64 * 1024;

    // Taille du buffer de lecture (8 Ko)
    private static final int BUFFER_SIZE = 8 * 1024;

    /**
     * Divise un fichier en chunks en utilisant l'algorithme de Rabin Fingerprinting.
     *
     * @param filePath Le chemin du fichier à diviser.
     * @return Une liste de chunks (tableaux d'octets).
     * @throws IOException Si une erreur survient lors de la lecture du fichier.
     * @throws IllegalArgumentException Si le fichier n'existe pas ou est inaccessible.
     */
    public List<byte[]> chunkFile(String filePath) throws IOException {
        long startTime = System.nanoTime();
        Objects.requireNonNull(filePath, "Le chemin du fichier ne peut pas etre null");

        File file = new File(filePath);
        if (!file.exists() || !file.canRead()) {
            throw new IllegalArgumentException("Le fichier n'existe pas ou n'est pas accessible : " + filePath);
        }

        List<byte[]> chunks = new ArrayList<>();
        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(file), BUFFER_SIZE)) {
            RabinFingerprintLong rabin = new RabinFingerprintLong(POLYNOMIAL);
            ByteArrayOutputStream chunkBuffer = new ByteArrayOutputStream();
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                for (int i = 0; i < bytesRead; i++) {
                    byte currentByte = buffer[i];
                    chunkBuffer.write(currentByte);
                    rabin.pushByte(currentByte);

                    if ((rabin.getFingerprintLong() & MASK) == 0) {
                        chunks.add(chunkBuffer.toByteArray());
                        logger.debug("Chunk cree : {} octets", chunkBuffer.size());
                        chunkBuffer.reset();
                        rabin = new RabinFingerprintLong(POLYNOMIAL);
                    } else if (chunkBuffer.size() >= MAX_CHUNK_SIZE) {
                        chunks.add(chunkBuffer.toByteArray());
                        logger.debug("Chunk maximal cree : {} octets", chunkBuffer.size());
                        chunkBuffer.reset();
                        rabin = new RabinFingerprintLong(POLYNOMIAL);
                    }
                }
            }

            if (chunkBuffer.size() > 0) {
                chunks.add(chunkBuffer.toByteArray());
            }
        }

        long endTime = System.nanoTime();
        logger.info("Fichier divise en {} chunks en {}ms", chunks.size(), (endTime - startTime) / 1_000_000);
        return chunks;
    }
}