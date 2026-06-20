

// 1) Cleanup existing structures (Standard GDS 2.x syntax)
CALL gds.graph.exists('rki') YIELD exists WHERE exists CALL gds.graph.drop('rki') YIELD graphName FINISH;
CALL gds.graph.exists('transmits') YIELD exists WHERE exists CALL gds.graph.drop('transmits') YIELD graphName FINISH;
CALL gds.graph.exists('transmits13') YIELD exists WHERE exists CALL gds.graph.drop('transmits13') YIELD graphName FINISH;
CALL gds.model.exists('graphSageModel') YIELD exists WHERE exists CALL gds.model.drop('graphSageModel') YIELD modelName FINISH;
CALL gds.model.exists('linkPredictionModel') YIELD exists WHERE exists CALL gds.model.drop('linkPredictionModel') YIELD modelName FINISH;
CALL gds.pipeline.exists('linkPredictionPipeline') YIELD exists WHERE exists CALL gds.pipeline.drop('linkPredictionPipeline') YIELD pipelineName FINISH;

// 2) Project Graph (Production Tier)
CALL gds.graph.project(
  'rki',
  { Patient: { properties: ['AMI_C','CPR_C','EMB_C','ETH_C','FLQ_C','INH_C','KAN_C','LZD_C','PAS_C','PZA_C','RIF_C','SM_C'] } },
  { TRANSMITS: { type: 'TRANSMITS', orientation: 'UNDIRECTED', properties: ['weight'] } }
) YIELD graphName FINISH;

// Filtering to your transmission threshold
CALL gds.graph.filter('transmits13', 'rki', '*', 'r.weight >= 0.076923077') YIELD graphName AS filteredGraph FINISH;

// -----------------------------
// 5) Train GraphSAGE model on transmits13
//    Use the scalar properties as feature vector inputs.
//    GraphSAGE requires numeric properties available on all nodes.
// -----------------------------
CALL gds.beta.graphSage.train('transmits13', {
  modelName: 'graphSageModel',
  featureProperties: [
    'INH_C','PAS_C','CPR_C','FLQ_C','RIF_C','PZA_C',
    'LZD_C','ETH_C','KAN_C','SM_C','EMB_C','AMI_C'
  ],
  embeddingDimension: $graphsageparams.embeddingDimension,
  randomSeed: $generalparams.randomSeed,
  aggregator: $graphsageparams.aggregator,
  epochs: $graphsageparams.epochs,
  learningRate: $graphsageparams.learningRate
})
YIELD modelInfo AS modelInfoGraphSage FINISH;

// 3) Create Pipeline (Production Tier)
CALL gds.beta.pipeline.linkPrediction.create('linkPredictionPipeline') YIELD name AS namePipe;

//---------



// -----------------------------
// 7) Add ONE GraphSAGE embedding node property (single embedding vector)
//    The mutate step will populate a vector property (embedding) on nodes.
// -----------------------------
CALL gds.beta.pipeline.linkPrediction.addNodeProperty(
  'linkPredictionPipeline',
  'gds.beta.graphSage.mutate',
  {
    mutateProperty: 'embedding',
    modelName: 'graphSageModel'
  }
)
YIELD name AS nodePropStepName, nodePropertySteps AS nodePropertySteps FINISH;

// -----------------------------
// 8) Add link feature: cosine similarity of the embedding vectors
// -----------------------------
CALL gds.beta.pipeline.linkPrediction.addFeature(
  'linkPredictionPipeline',
  'cosine',
  {
    nodeProperties: ['embedding']
  }
)
YIELD name AS featureName, featureSteps FINISH;

// -----------------------------
// 9) Configure split for training/validation/test
// -----------------------------
CALL gds.beta.pipeline.linkPrediction.configureSplit(
  'linkPredictionPipeline',
  {
    negativeSamplingRatio: $generalparams.negativeSamplingRatio,
    testFraction: $generalparams.testFraction,
    trainFraction: $generalparams.trainFraction,
    validationFolds: $generalparams.validationFolds
  }
)
YIELD name AS splitName, splitConfig FINISH;

// -----------------------------
// 10) Add MLP classifier (use beta API to match the pipeline create)
// -----------------------------
CALL gds.alpha.pipeline.linkPrediction.addMLP(
  'linkPredictionPipeline',
  {
    hiddenLayerSizes: [$generalparams.hiddenLayerSize1, $generalparams.hiddenLayerSize2],
    penalty: $generalparams.penalty,
    patience: $generalparams.patience
  }
)
YIELD parameterSpace FINISH;
//---------

// -----------------------------
// 11) Train the pipeline on the 'transmits13' graph
// -----------------------------
CALL gds.beta.pipeline.linkPrediction.train(
  'transmits13',
  {
    pipeline: 'linkPredictionPipeline',
    targetRelationshipType: 'TRANSMITS',
    modelName: 'linkPredictionModel',
    randomSeed: $generalparams.randomSeed,
    metrics: ['AUCPR']
  }
)
YIELD modelInfo 

// 5) Calculate Stats and Stream Predictions
WITH modelInfo
CALL gds.degree.stats('transmits13') YIELD centralityDistribution
CALL gds.localClusteringCoefficient.stats('transmits13') YIELD averageClusteringCoefficient

WITH modelInfo, centralityDistribution, averageClusteringCoefficient
CALL gds.beta.pipeline.linkPrediction.predict.stream('transmits13', {
  modelName: 'linkPredictionModel',
  topN: $generalparams.topN,
  threshold: $generalparams.threshold
}) YIELD node1, node2, probability

WITH gds.util.asNode(node1) AS n1, gds.util.asNode(node2) AS n2, probability, modelInfo, centralityDistribution, averageClusteringCoefficient
MATCH (n1)-[t:TRANSMITS]-(n2)

RETURN 
  modelInfo.metrics.AUCPR.test AS auprTest,
  modelInfo.metrics.AUCPR.validation.avg AS auprValidation,
  modelInfo.metrics.AUCPR.outerTrain AS auprOuterTrain,
  centralityDistribution.mean AS avgDegree,
  averageClusteringCoefficient AS avgClustering,
  
  // --- Prediction Data ---
  n1.Isolate_ID AS patient1,
  n1.Isolation_Country,
  SIZE(n1.drug_resistance) AS resistance1,
  SIZE(n1.full_mutation_list) AS mutation1,
  n2.Isolate_ID AS patient2,
  n2.Isolation_Country,
  SIZE(n2.drug_resistance) AS resistance2,
  SIZE(n2.full_mutation_list) AS mutation2,
  probability,
  
 CASE WHEN SIZE(n1.drug_resistance + [r IN n2.drug_resistance WHERE NOT r IN n1.drug_resistance]) = 0 
     THEN null 
     ELSE SIZE([r IN n1.drug_resistance WHERE r IN n2.drug_resistance]) * 1.0 
          / SIZE(n1.drug_resistance + [r IN n2.drug_resistance WHERE NOT r IN n1.drug_resistance]) 
END AS jaccardSimilarity,

CASE WHEN SIZE(n1.full_mutation_list + [r IN n2.full_mutation_list WHERE NOT r IN n1.full_mutation_list]) = 0 
     THEN null 
     ELSE SIZE([r IN n1.full_mutation_list WHERE r IN n2.full_mutation_list]) * 1.0 
          / SIZE(n1.full_mutation_list + [r IN n2.full_mutation_list WHERE NOT r IN n1.full_mutation_list]) 
END AS jacSimMut,
  
  SIZE([r IN n2.drug_resistance WHERE NOT r IN n1.drug_resistance]) + 
  SIZE([r IN n1.drug_resistance WHERE NOT r IN n2.drug_resistance]) AS distance, 
  
  SIZE([r IN n2.full_mutation_list WHERE NOT r IN n1.full_mutation_list]) + 
  SIZE([r IN n1.full_mutation_list WHERE NOT r IN n2.full_mutation_list]) AS mutdistance,
  
  CASE WHEN t.weight = 0 THEN null ELSE 1.0 / t.weight END AS transmitsScore 

ORDER BY jaccardSimilarity DESC, jacSimMut DESC, distance DESC, mutdistance DESC, probability DESC, patient1
