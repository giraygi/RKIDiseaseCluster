CALL gds.graph.exists('rki') YIELD exists
WITH exists
WHERE exists = true
CALL gds.graph.drop('rki')
YIELD graphName
RETURN 'Graph dropped: ' + graphName AS message;

CALL gds.graph.exists('transmits') YIELD exists
WITH exists
WHERE exists = true
CALL gds.graph.drop('transmits')
YIELD graphName
RETURN 'Graph dropped: ' + graphName AS message;

CALL gds.graph.exists('transmits13') YIELD exists
WITH exists
WHERE exists = true
CALL gds.graph.drop('transmits13')YIELD graphName
RETURN 'Graph dropped: ' + graphName AS message;

CALL gds.beta.pipeline.exists('linkPredictionPipeline') YIELD exists
WITH exists
WHERE exists = true
CALL gds.beta.pipeline.drop('linkPredictionPipeline')
YIELD pipelineName
RETURN 'Pipeline dropped: ' + pipelineName AS message;

CALL gds.model.exists('linkPredictionModel') YIELD exists
WITH exists
WHERE exists = true
CALL gds.model.drop('linkPredictionModel')
YIELD modelName
RETURN 'Model dropped: ' + modelName AS message;


//CALL gds.graph.drop('transmits') YIELD graphName;
//CALL gds.graph.drop('transmits13') YIELD graphName;
//CALL gds.beta.pipeline.drop('linkPredictionPipeline');
//CALL gds.model.drop('linkPredictionModel');

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
YIELD graphName AS graph, nodeProjection, nodeCount AS nodes, relationshipProjection, relationshipCount AS rel

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

CALL gds.beta.pipeline.linkPrediction.addNodeProperty(
  'linkPredictionPipeline',
  'gds.node2vec.mutate',
  {
    mutateProperty: 'embedding',      // this will be created by the pipeline
     walkLength: 80,
    iterations: 100,
    embeddingDimension: 128,
    randomSeed: 42
  }
)
YIELD name AS name1, nodePropertySteps AS nps1

// Add link features
CALL gds.beta.pipeline.linkPrediction.addFeature(
  'linkPredictionPipeline', 
  'cosine', 
  { nodeProperties: [
      'INH_C','PAS_C','CPR_C','FLQ_C','RIF_C',
      'PZA_C','LZD_C','ETH_C','KAN_C','SM_C','EMB_C','AMI_C'
    ] 
  }
)
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

//CALL gds.beta.pipeline.linkPrediction.predict.stream('transmits13', {
//  modelName: 'linkPredictionModel',
//  relationshipTypes: ['TRANSMITS'],
//  topN: 100,
//  threshold: 0.2
//}) YIELD node1, node2, probability
// RETURN gds.util.asNode(node1).Isolate_ID AS patient1, gds.util.asNode(node1).drug_resistance AS resistance1, gds.util.asNode(node2).Isolate_ID AS patient2, gds.util.asNode(node2).drug_resistance AS resistance2, probability
// ORDER BY probability DESC, patient1


CALL gds.beta.pipeline.linkPrediction.predict.stream('transmits13', {
  modelName: 'linkPredictionModel',
  relationshipTypes: ['TRANSMITS'],
  topN: 4000,
  threshold: 0.2
}) YIELD node1, node2, probability
WITH 
  gds.util.asNode(node1) AS n1, 
  gds.util.asNode(node2) AS n2, 
  probability
MATCH (n1)-[t:TRANSMITS]-(n2)
RETURN 
  n1.Isolate_ID AS patient1,
  n1.Isolation_Country,
  SIZE(n1.drug_resistance) AS resistance1,
  SIZE(n1.full_mutation_list) AS mutation1,
  n2.Isolate_ID AS patient2,
  n2.Isolation_Country,
  SIZE(n2.drug_resistance) AS resistance2,
  SIZE(n2.full_mutation_list) AS mutation2,
  probability,
  // Calculate Jaccard similarity
  SIZE([r IN n1.drug_resistance WHERE r IN n2.drug_resistance]) * 1.0 
  / 
  SIZE(n1.drug_resistance + [r IN n2.drug_resistance WHERE NOT r IN n1.drug_resistance])  
  AS jaccardSimilarity,
 // Calculate Jaccard similarity
  SIZE([r IN n1.full_mutation_list WHERE r IN n2.full_mutation_list]) * 1.0 
  / 
  SIZE(n1.full_mutation_list + [r IN n2.full_mutation_list WHERE NOT r IN n1.full_mutation_list])  
  AS jacSimMut,
  SIZE([r IN n2.drug_resistance WHERE NOT r IN n1.drug_resistance])+SIZE([r IN n1.drug_resistance WHERE NOT r IN n2.drug_resistance]) as distance,
  SIZE([r IN n2.full_mutation_list WHERE NOT r IN n1.full_mutation_list])+SIZE([r IN n1.full_mutation_list WHERE NOT r IN n2.full_mutation_list]) as mutdistance,
  1/t.weight AS transmitsScore // <-- include weight of TRANSMITS relationship (if exists)
ORDER BY jaccardSimilarity DESC, jacSimMut DESC, distance DESC, mutdistance DESC, probability DESC, patient1

// YIELD relationshipsWritten, samplingStats RETURN relationshipsWritten, samplingStats
