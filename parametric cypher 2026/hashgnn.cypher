// 1) Cleanup existing structures (Standard GDS 2.x syntax)
CALL gds.graph.exists('rki') YIELD exists WHERE exists CALL gds.graph.drop('rki') YIELD graphName FINISH;
CALL gds.graph.exists('transmits') YIELD exists WHERE exists CALL gds.graph.drop('transmits') YIELD graphName FINISH;
CALL gds.graph.exists('transmits13') YIELD exists WHERE exists CALL gds.graph.drop('transmits13') YIELD graphName FINISH;
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

// 3) Create Pipeline (Production Tier)
CALL gds.beta.pipeline.linkPrediction.create('linkPredictionPipeline') YIELD name AS namePipe;

// Add Node Property (HashGNN is still beta in many 2.x versions, but using modern lp.addNodeProperty)
CALL gds.beta.pipeline.linkPrediction.addNodeProperty('linkPredictionPipeline', 'gds.hashgnn.mutate', {
    mutateProperty: 'embedding',
    featureProperties: ['INH_C','PAS_C','CPR_C','FLQ_C','RIF_C','PZA_C','LZD_C','ETH_C','KAN_C','SM_C','EMB_C','AMI_C'],
    iterations: $hashgnnparams.iterations,
    embeddingDensity: $hashgnnparams.embeddingDensity,
    binarizeFeatures: {dimension: $hashgnnparams.bfdimension, threshold: $hashgnnparams.bfthreshold},
    randomSeed: $generalparams.randomSeed
}) YIELD nodePropertySteps FINISH;

// Add link features
CALL gds.beta.pipeline.linkPrediction.addFeature('linkPredictionPipeline', 'cosine', { nodeProperties: ['embedding'] }) YIELD featureSteps FINISH;

// Configure Split
CALL gds.beta.pipeline.linkPrediction.configureSplit('linkPredictionPipeline', {
  testFraction: $generalparams.testFraction,
  trainFraction: $generalparams.trainFraction,
  validationFolds: $generalparams.validationFolds,
  negativeSamplingRatio: $generalparams.negativeSamplingRatio
}) YIELD splitConfig FINISH;

// Add MLP (Production Tier)
// To fix the NULL AUC, we pass a list for penalty to trigger model selection
CALL gds.alpha.pipeline.linkPrediction.addMLP('linkPredictionPipeline', {
  hiddenLayerSizes: [$generalparams.hiddenLayerSize1, $generalparams.hiddenLayerSize2],
  penalty: $generalparams.penalty, // Use a list of values to ensure selection stats populate
  patience: $generalparams.patience
}) YIELD parameterSpace FINISH;

// 4) Train with explicit Evaluation Metrics
CALL gds.beta.pipeline.linkPrediction.train('transmits13', {
  pipeline: 'linkPredictionPipeline',
  modelName: 'linkPredictionModel',
  targetRelationshipType: 'TRANSMITS',
  randomSeed: $generalparams.randomSeed
  //metrics: ['AUC', 'OUT_OF_BAG_ERROR'] // Explicitly request AUC calculation
}) YIELD modelInfo

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
  modelInfo.modelSelectionStats.bestParameters AS bestParams,
  coalesce(modelInfo.modelSelectionStats.validationResults.auc.mean, 0) AS meanAUC, 
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
