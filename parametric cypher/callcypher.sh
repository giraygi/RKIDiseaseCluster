#!/usr/bin/env bash
args=("$@")
echo ${args[0]}" cypher will be executed and output will be written into ${args[1]}.csv"
echo "The following parameters are being taken from ${args[2]}.json :"
cat ${args[2]}.json
readarray -t PARAMS < <(
  jq -r '
    to_entries |
    map(
      "--param=\(.key)=>{" +
      (
        .value
        | to_entries
        | map("\(.key):\(.value|@json)")
        | join(",")
      )
      + "}"
    )[]
  ' ${args[2]}.json
)

#
../bin/cypher-shell -a neo4j://127.0.0.1:7687 --non-interactive -f ${args[0]}.cypher -u neo4j -p deneme79 "${PARAMS[@]}" --format plain > ${args[1]}.csv

