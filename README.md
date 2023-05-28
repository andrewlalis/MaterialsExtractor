# MaterialsExtractor
Program to extract a list of materials from an NBT structure file.

## Usage
```shell
git clone git@github.com:andrewlalis/MaterialsExtractor.git
cd MaterialsExtractor
./build.sh
```

Usage information:
```
java -jar materials-extractor.jar <schematic-file.nbt> [paste.ee token]
  Where
    First argument (required) is the path to the .nbt schematic file to read.
    Second argument (optional) is an api token to https://paste.ee, to
      automatically upload item-list for usage.
```
