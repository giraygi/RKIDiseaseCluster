package dtn.cluster;
import org.neo4j.graphdb.RelationshipType;


enum RelTypes implements RelationshipType
{
    TRANSMITS, // Genetic Similarity
    MUTATES, // Number of Shared Mutations
    DIFFERS // Number of Shared Mutations that Cause a Drug Resistance
}