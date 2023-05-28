#!/usr/bin/env bash

./mvnw clean package
cp target/materials-extractor-*-jar-with-dependencies.jar ./materials-extractor.jar

echo "Built project as materials-extractor.jar"
echo "Run: java -jar materials-extractor.jar <schematic-file.nbt> [paste.ee token]"
