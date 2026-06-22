#!/usr/bin/env bash
mkdir -p outputs
for algo in hashgnn node2vec graphsage; do
  for i in 1 2 3 4 5 6 7 8 9 10 11; do
    output="june_${algo}_${i}"
    params="params${algo}${i}"
    echo "Running $algo variant $i -> outputs/$output.csv"
    ./callcypher.sh $algo outputs/$output parameters/$params
  done
done
rm metrics_summary.csv
python3 evaluate.py outputs/june_hashgnn_*.csv outputs/june_node2vec_*.csv outputs/june_graphsage_*.csv
