// -----------------------------
// 0) Parameters you can tweak
// -----------------------------
WITH 0.076923077 AS WEIGHT_THRESHOLD, 42 AS RANDOM_SEED, 256 AS EMBEDDING_DIMENSION, 4000 AS TOP_N

// -----------------------------
// 1) DROP existing graphs/pipelines/models if they exist
// -----------------------------
// -----------------------------
// Drop existing graphs, models, pipelines if they exist
// -----------------------------
CALL {
  CALL gds.graph.exists('rki') YIELD exists
  WITH exists WHERE exists
  CALL gds.graph.drop('rki') YIELD graphName
  RETURN 'Dropped graph: ' + graphName AS message
} 
CALL {
  CALL gds.graph.exists('transmits') YIELD exists
  WITH exists WHERE exists
  CALL gds.graph.drop('transmits') YIELD graphName
  RETURN 'Dropped graph: ' + graphName AS message2
} 
CALL {
  CALL gds.graph.exists('transmits13') YIELD exists
  WITH exists WHERE exists
  CALL gds.graph.drop('transmits13') YIELD graphName
  RETURN 'Dropped graph: ' + graphName AS message3
} 
CALL {
  CALL gds.beta.pipeline.exists('linkPredictionPipeline') YIELD exists
  WITH exists WHERE exists
  CALL gds.beta.pipeline.drop('linkPredictionPipeline') YIELD pipelineName
  RETURN 'Dropped pipeline: ' + pipelineName AS message4
} 
CALL {
  CALL gds.model.exists('graphSageModel') YIELD exists
  WITH exists WHERE exists
  CALL gds.model.drop('graphSageModel') YIELD modelName
  RETURN 'Dropped model: ' + modelName AS messag5
} 
CALL {
  CALL gds.model.exists('linkPredictionModel') YIELD exists
  WITH exists WHERE exists
  CALL gds.model.drop('linkPredictionModel') YIELD modelName
  RETURN 'Dropped model: ' + modelName AS message6
} 
RETURN 'All cleanup steps executed' AS info;

// -----------------------------
// 2) Ensure numeric feature properties exist on all :Patient nodes
//    (set missing numeric features to 0)
// -----------------------------
MATCH (p:Patient)
SET p.AMI_C = coalesce(p.AMI_C, 0),
    p.CPR_C = coalesce(p.CPR_C, 0),
    p.EMB_C = coalesce(p.EMB_C, 0),
    p.ETH_C = coalesce(p.ETH_C, 0),
    p.FLQ_C = coalesce(p.FLQ_C, 0),
    p.INH_C = coalesce(p.INH_C, 0),
    p.KAN_C = coalesce(p.KAN_C, 0),
    p.LZD_C = coalesce(p.LZD_C, 0),
    p.PAS_C = coalesce(p.PAS_C, 0),
    p.PZA_C = coalesce(p.PZA_C, 0),
    p.RIF_C = coalesce(p.RIF_C, 0),
    p.SM_C  = coalesce(p.SM_C, 0)
RETURN 'Ensured features present on all Patient nodes' AS info;

// -----------------------------
// 3) Project the main graph 'rki' with the scalar features available
// -----------------------------
CALL gds.graph.project(
  'rki',
  {
    Patient: {
      properties: [
        'AMI_C','CPR_C','EMB_C','ETH_C','FLQ_C','INH_C',
        'KAN_C','LZD_C','PAS_C','PZA_C','RIF_C','SM_C'
      ]
    }
  },
  {
    TRANSMITS: {
      type: 'TRANSMITS',
      orientation: 'UNDIRECTED',
      properties: ['weight']
    }
  }
)
YIELD graphName, nodeCount, relationshipCount;

// -----------------------------
// 4) Create derived graphs via gds.graph.filter
//    - transmits: keep only TRANSMITS relationships
//    - transmits13: keep relationships with weight >= WEIGHT_THRESHOLD
// -----------------------------
CALL gds.graph.filter(
'transmits',  // Name of the new graph
'rki',  // Name of the original graph
'*',  // Node filter
'r:TRANSMITS'  // Relationship filter
)
YIELD graphName AS transmitsGraph, nodeCount AS transNodeCount, relationshipCount AS transRelCount;

CALL gds.graph.filter(
'transmits13',  // Name of the new graph
'transmits',  // Name of the original graph
'*',  // Node filter
'r.weight >= 0.076923077'  // Relationship filter
)
YIELD graphName AS transmits13Graph, nodeCount AS t13NodeCount, relationshipCount AS t13RelCount;

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
  embeddingDimension: 256,
  randomSeed: 42,
  aggregator: 'mean',
  epochs: 50,
  learningRate: 0.01
})
YIELD modelInfo AS modelInfoGraphSage;

// -----------------------------
// 6) Create link-prediction pipeline
// -----------------------------
CALL gds.beta.pipeline.linkPrediction.create('linkPredictionPipeline')
YIELD name AS pipelineName;

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
YIELD name AS nodePropStepName, nodePropertySteps AS nodePropertySteps;

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
YIELD name AS featureName, featureSteps;

// -----------------------------
// 9) Configure split for training/validation/test
// -----------------------------
CALL gds.beta.pipeline.linkPrediction.configureSplit(
  'linkPredictionPipeline',
  {
    negativeSamplingRatio: 1.0,
    testFraction: 0.1,
    trainFraction: 0.8,
    validationFolds: 3
  }
)
YIELD name AS splitName, splitConfig;

// -----------------------------
// 10) Add MLP classifier (use beta API to match the pipeline create)
// -----------------------------
CALL gds.alpha.pipeline.linkPrediction.addMLP(
  'linkPredictionPipeline',
  {
    hiddenLayerSizes: [8, 4],
    penalty: 1,
    patience: 2
  }
)
YIELD parameterSpace;

// -----------------------------
// 11) Train the pipeline on the 'transmits13' graph
// -----------------------------
CALL gds.beta.pipeline.linkPrediction.train(
  'transmits13',
  {
    pipeline: 'linkPredictionPipeline',
    targetRelationshipType: 'TRANSMITS',
    modelName: 'linkPredictionModel',
    randomSeed: 42
  }
)
YIELD modelInfo AS trainedPipelineModelInfo;

// -----------------------------
// 12) Run prediction (stream topN predictions above threshold) and return enriched results
// -----------------------------
CALL gds.beta.pipeline.linkPrediction.predict.stream(
  'transmits13',
  {
    modelName: 'linkPredictionModel',
    relationshipTypes: ['TRANSMITS'],
    topN: 4000,
    threshold: 0.2
  }
) YIELD node1, node2, probability
WITH gds.util.asNode(node1) AS n1, gds.util.asNode(node2) AS n2, probability
MATCH (n1)-[t:TRANSMITS]-(n2)
RETURN 
  n1.Isolate_ID AS patient1,
  n1.Isolation_Country AS country1,
  SIZE(coalesce(n1.drug_resistance, [])) AS resistance1,
  SIZE(coalesce(n1.full_mutation_list, [])) AS mutation1,
  n2.Isolate_ID AS patient2,
  n2.Isolation_Country AS country2,
  SIZE(coalesce(n2.drug_resistance, [])) AS resistance2,
  SIZE(coalesce(n2.full_mutation_list, [])) AS mutation2,
  probability,
  // Jaccard similarity for drug_resistance (safe with coalesce)
  (SIZE([r IN coalesce(n1.drug_resistance,[]) WHERE r IN coalesce(n2.drug_resistance,[])]) * 1.0)
  /
  (CASE WHEN SIZE(coalesce(n1.drug_resistance,[])) + SIZE(coalesce(n2.drug_resistance,[])) = 0 THEN 1.0
        ELSE SIZE(n1.drug_resistance + [r IN n2.drug_resistance WHERE NOT r IN n1.drug_resistance]) END)
  AS jaccardSimilarity,
  // Jaccard for mutation lists
  (SIZE([r IN coalesce(n1.full_mutation_list,[]) WHERE r IN coalesce(n2.full_mutation_list,[])]) * 1.0)
  /
  (CASE WHEN SIZE(coalesce(n1.full_mutation_list,[])) + SIZE(coalesce(n2.full_mutation_list,[])) = 0 THEN 1.0
        ELSE SIZE(n1.full_mutation_list + [r IN n2.full_mutation_list WHERE NOT r IN n1.full_mutation_list]) END)
  AS jacSimMut,
  // symmetric difference sizes
  (SIZE([r IN coalesce(n2.drug_resistance,[]) WHERE NOT r IN coalesce(n1.drug_resistance,[])]) +
   SIZE([r IN coalesce(n1.drug_resistance,[]) WHERE NOT r IN coalesce(n2.drug_resistance,[])])) AS distance,
  (SIZE([r IN coalesce(n2.full_mutation_list,[]) WHERE NOT r IN coalesce(n1.full_mutation_list,[])]) +
   SIZE([r IN coalesce(n1.full_mutation_list,[]) WHERE NOT r IN coalesce(n2.full_mutation_list,[])])) AS mutdistance,
  CASE WHEN exists((n1)-[t]-()) AND t.weight IS NOT NULL THEN 1.0 / t.weight ELSE NULL END AS transmitsScore
ORDER BY jaccardSimilarity DESC, jacSimMut DESC, distance DESC, mutdistance DESC, probability DESC, patient1
LIMIT 4000;
