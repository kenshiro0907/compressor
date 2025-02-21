package org.goldken;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Utilitaire pour le calcul de hash.
 */
public final class Utils {

    // Algorithme de hachage par défaut
    private static final String DEFAULT_HASH_ALGORITHM = "SHA-256";

    /**
     * Calcule le hash d'un chunk en utilisant l'algorithme SHA-256.
     *
     * @param chunk Le tableau d'octets à hacher.
     * @return Le hash sous forme de chaîne hexadécimale.
     * @throws HashException Si l'algorithme de hachage n'est pas disponible.
     * @throws IllegalArgumentException Si le chunk est null.
     */
    public static String computeSha256Hash(byte[] chunk) throws HashException {
        Objects.requireNonNull(chunk, "Le chunk ne peut pas être null");

        try {
            MessageDigest digest = MessageDigest.getInstance(DEFAULT_HASH_ALGORITHM);
            byte[] hash = digest.digest(chunk);
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new HashException("Algorithme de hachage non disponible : " + DEFAULT_HASH_ALGORITHM, e);
        }
    }

    /**
     * Convertit un tableau d'octets en une chaîne hexadécimale.
     *
     * @param bytes Le tableau d'octets à convertir.
     * @return La chaîne hexadécimale.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }

    /**
     * Exception personnalisée pour les erreurs de hachage.
     */
    public static class HashException extends Exception {
        public HashException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}