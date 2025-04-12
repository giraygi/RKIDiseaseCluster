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
YIELD name AS namePipe

CALL gds.beta.pipeline.linkPrediction.addNodeProperty('linkPredictionPipeline', 'gds.hashgnn.mutate', {
mutateProperty: 'INH_C',
iterations: 10,
embeddingDensity: 2,
generateFeatures: {dimension: 6, densityLevel: 1},
randomSeed: 42
})
YIELD name AS name1, nodePropertySteps AS nps1

CALL gds.beta.pipeline.linkPrediction.addNodeProperty('linkPredictionPipeline', 'gds.hashgnn.mutate', {
mutateProperty: 'PAS_C',
iterations: 10,
embeddingDensity: 2,
generateFeatures: {dimension: 6, densityLevel: 1},
randomSeed: 42
})
YIELD name AS name2, nodePropertySteps AS nps2

CALL gds.beta.pipeline.linkPrediction.addNodeProperty('linkPredictionPipeline', 'gds.hashgnn.mutate', {
mutateProperty: 'CPR_C',
iterations: 10,
embeddingDensity: 2,
generateFeatures: {dimension: 6, densityLevel: 1},
randomSeed: 42
})
YIELD name AS name3, nodePropertySteps AS nps3

CALL gds.beta.pipeline.linkPrediction.addNodeProperty('linkPredictionPipeline', 'gds.hashgnn.mutate', {
mutateProperty: 'FLQ_C',
iterations: 10,
embeddingDensity: 2,
generateFeatures: {dimension: 6, densityLevel: 1},
randomSeed: 42
})
YIELD name AS name4, nodePropertySteps AS nps4

CALL gds.beta.pipeline.linkPrediction.addNodeProperty('linkPredictionPipeline', 'gds.hashgnn.mutate', {
mutateProperty: 'RIF_C',
iterations: 10,
embeddingDensity: 2,
generateFeatures: {dimension: 6, densityLevel: 1},
randomSeed: 42
})
YIELD name AS name5, nodePropertySteps AS nps5

CALL gds.beta.pipeline.linkPrediction.addNodeProperty('linkPredictionPipeline', 'gds.hashgnn.mutate', {
mutateProperty: 'PZA_C',
iterations: 10,
embeddingDensity: 2,
generateFeatures: {dimension: 6, densityLevel: 1},
randomSeed: 42
})
YIELD name AS name6, nodePropertySteps AS nps6

CALL gds.beta.pipeline.linkPrediction.addNodeProperty('linkPredictionPipeline', 'gds.hashgnn.mutate', {
mutateProperty: 'LZD_C',
iterations: 10,
embeddingDensity: 2,
generateFeatures: {dimension: 6, densityLevel: 1},
randomSeed: 42
})
YIELD name AS name7, nodePropertySteps AS nps7

CALL gds.beta.pipeline.linkPrediction.addNodeProperty('linkPredictionPipeline', 'gds.hashgnn.mutate', {
mutateProperty: 'ETH_C',
iterations: 10,
embeddingDensity: 2,
generateFeatures: {dimension: 6, densityLevel: 1},
randomSeed: 42
})
YIELD name AS name8, nodePropertySteps AS nps8

CALL gds.beta.pipeline.linkPrediction.addNodeProperty('linkPredictionPipeline', 'gds.hashgnn.mutate', {
mutateProperty: 'KAN_C',
iterations: 10,
embeddingDensity: 2,
generateFeatures: {dimension: 6, densityLevel: 1},
randomSeed: 42
})
YIELD name AS name9, nodePropertySteps AS nps9

CALL gds.beta.pipeline.linkPrediction.addNodeProperty('linkPredictionPipeline', 'gds.hashgnn.mutate', {
mutateProperty: 'SM_C',
iterations: 10,
embeddingDensity: 2,
generateFeatures: {dimension: 6, densityLevel: 1},
randomSeed: 42
})
YIELD name AS name10, nodePropertySteps AS nps10

CALL gds.beta.pipeline.linkPrediction.addNodeProperty('linkPredictionPipeline', 'gds.hashgnn.mutate', {
mutateProperty: 'EMB_C',
iterations: 10,
embeddingDensity: 2,
generateFeatures: {dimension: 6, densityLevel: 1},
randomSeed: 42
})
YIELD name AS name11, nodePropertySteps AS nps11

CALL gds.beta.pipeline.linkPrediction.addNodeProperty('linkPredictionPipeline', 'gds.hashgnn.mutate', {
mutateProperty: 'AMI_C',
iterations: 10,
embeddingDensity: 2,
generateFeatures: {dimension: 6, densityLevel: 1},
randomSeed: 42
})
YIELD name AS name12, nodePropertySteps AS nps12

// Add link features
CALL gds.beta.pipeline.linkPrediction.addFeature('linkPredictionPipeline', 'cosine', {
nodeProperties: ['INH_C','PAS_C','CPR_C','FLQ_C','RIF_C','PZA_C','LZD_C','ETH_C','KAN_C','SM_C','EMB_C','AMI_C'],
graphName: 'transmits13'  // Use the filtered graph
})
YIELD name AS nameFeature, featureSteps

// Configure the split
CALL gds.beta.pipeline.linkPrediction.configureSplit('linkPredictionPipeline', {
negativeSamplingRatio: 1.0,
testFraction: 0.1,
trainFraction: 0.8,
validationFolds: 3
})
YIELD name AS nameSplit, splitConfig

CALL gds.alpha.pipeline.linkPrediction.addMLP('linkPredictionPipeline',
{hiddenLayerSizes: [6, 3], penalty: 1, patience: 2}) YIELD parameterSpace

// Train the pipeline
CALL gds.beta.pipeline.linkPrediction.train('transmits13', {
pipeline: 'linkPredictionPipeline',
targetRelationshipType: 'TRANSMITS',
modelName: 'linkPredictionModel',
randomSeed: 42
})
YIELD modelInfo

CALL gds.beta.pipeline.linkPrediction.predict.stream('transmits13', {
  modelName: 'linkPredictionModel',
  relationshipTypes: ['TRANSMITS'],
  topN: 100,
  threshold: 0.2
}) YIELD node1, node2, probability
 RETURN gds.util.asNode(node1).Isolate_ID AS patient1, gds.util.asNode(node1).drug_resistance AS resistance1, gds.util.asNode(node2).Isolate_ID AS patient2, gds.util.asNode(node2).drug_resistance AS resistance2, probability
 ORDER BY probability DESC, patient1





// YIELD relationshipsWritten, samplingStats RETURN relationshipsWritten, samplingStats
