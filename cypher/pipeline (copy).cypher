CALL gds.graph.project(
  'rki',
  'Patient',
  {
    TRANSMITS: {
      orientation: 'UNDIRECTED',
      properties: 'weight'
    }
  }
)
YIELD
  graphName AS graph, nodeProjection, nodeCount AS nodes, relationshipProjection, relationshipCount AS rel

CALL gds.graph.filter(
'transmits',  // Name of the new graph
'rki',  // Name of the original graph
'*',  // Node filter
'r:TRANSMITS'  // Relationship filter
)
YIELD graphName AS filteredGraph, fromGraphName AS fromGraphName1, nodeCount AS nc1, relationshipCount AS rc1


CALL gds.graph.filter(
'transmits13',  // Name of the new graph
'transmits',  // Name of the original graph
'*',  // Node filter
'r.weight >= 0.076923077'  // Relationship filter
)
YIELD graphName AS filteredTransmitsGraph, fromGraphName  AS fromGraphName2, nodeCount AS nc2, relationshipCount AS rc2

CALL gds.beta.pipeline.linkPrediction.create('linkPredictionPipeline')
YIELD name AS name1

CALL gds.beta.pipeline.linkPrediction.addNodeProperty('linkPredictionPipeline', 'gds.node2vec.mutate', {
mutateProperty: 'full_mutation_list'
})
YIELD name AS name2, nodePropertySteps



// Add link features
CALL gds.beta.pipeline.linkPrediction.addFeature('linkPredictionPipeline', 'l2', {
nodeProperties: ['full_mutation_list'],
graphName: 'transmits13'  // Use the filtered graph
})
YIELD name AS name3, featureSteps




// Configure the split
CALL gds.beta.pipeline.linkPrediction.configureSplit('linkPredictionPipeline', {
negativeSamplingRatio: 1.0,
testFraction: 0.1,
trainFraction: 0.8,
validationFolds: 3
})
YIELD name AS name4, splitConfig


CALL gds.alpha.pipeline.linkPrediction.addMLP('linkPredictionPipeline',
{hiddenLayerSizes: [4, 2], penalty: 1, patience: 2}) YIELD parameterSpace


// Train the pipeline
CALL gds.beta.pipeline.linkPrediction.train('transmits13', {
pipeline: 'linkPredictionPipeline',
targetRelationshipType: 'TRANSMITS',
modelName: 'linkPredictionModel',
randomSeed: 42
})
YIELD modelInfo



CALL gds.beta.pipeline.linkPrediction.predict.mutate('transmits13', {
  modelName: 'linkPredictionModel',
  relationshipTypes: ['TRANSMITS'],
  mutateRelationshipType: 'TRANSMITS_EXHAUSTIVE_PREDICTED',
  topN: 5,
  threshold: 0.5
}) YIELD relationshipsWritten, samplingStats RETURN relationshipsWritten, samplingStats
