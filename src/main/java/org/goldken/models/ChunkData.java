package org.goldken.models;

import java.util.Objects;

/**
 * Représente les données d'un chunk (morceau de fichier).
 * Un chunk est identifié par son hash, sa taille, son emplacement et un compteur de références.
 */
public class ChunkData {
    private final String hash;
    private final long size;
    private final String location;
    private int referenceCount;

    /**
     * Constructeur pour créer un nouveau chunk.
     *
     * @param hash     Le hash unique du chunk.
     * @param size     La taille du chunk en octets.
     * @param location L'emplacement du chunk (chemin du fichier ou URL).
     * @throws IllegalArgumentException Si le hash, la location sont null, ou si la taille est négative.
     */
    public ChunkData(String hash, long size, String location) {
        this.hash = Objects.requireNonNull(hash, "Le hash ne peut pas être null");
        this.size = validateSize(size);
        this.location = Objects.requireNonNull(location, "L'emplacement ne peut pas être null");
        this.referenceCount = 1; // Initialisé à 1 car le chunk est référencé au moins une fois à la création
    }

    /**
     * Valide que la taille du chunk n'est pas négative.
     *
     * @param size La taille du chunk.
     * @return La taille si elle est valide.
     * @throws IllegalArgumentException Si la taille est négative.
     */
    private long validateSize(long size) {
        if (size < 0) {
            throw new IllegalArgumentException("La taille du chunk ne peut pas être négative");
        }
        return size;
    }

    /**
     * Incrémente le compteur de références du chunk.
     */
    public void incrementReferenceCount() {
        this.referenceCount++;
    }

    /**
     * Retourne le hash du chunk.
     *
     * @return Le hash du chunk.
     */
    public String getHash() {
        return hash;
    }

    /**
     * Retourne la taille du chunk.
     *
     * @return La taille du chunk en octets.
     */
    public long getSize() {
        return size;
    }

    /**
     * Retourne l'emplacement du chunk.
     *
     * @return L'emplacement du chunk.
     */
    public String getLocation() {
        return location;
    }

    /**
     * Retourne le nombre de références du chunk.
     *
     * @return Le compteur de références.
     */
    public int getReferenceCount() {
        return referenceCount;
    }

    /**
     * Redéfinition de la méthode equals pour comparer deux chunks par leur hash.
     *
     * @param o L'objet à comparer.
     * @return true si les chunks ont le même hash, false sinon.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChunkData chunkData = (ChunkData) o;
        return hash.equals(chunkData.hash);
    }

    /**
     * Redéfinition de la méthode hashCode pour correspondre à la méthode equals.
     *
     * @return Le hash code du chunk basé sur son hash.
     */
    @Override
    public int hashCode() {
        return Objects.hash(hash);
    }

    /**
     * Redéfinition de la méthode toString pour afficher les informations du chunk.
     *
     * @return Une représentation textuelle du chunk.
     */
    @Override
    public String toString() {
        return "ChunkData{" +
                "hash='" + hash + '\'' +
                ", size=" + size +
                ", location='" + location + '\'' +
                ", referenceCount=" + referenceCount +
                '}';
    }
}