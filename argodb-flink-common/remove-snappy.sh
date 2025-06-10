rm -rf temp
mkdir temp
cp target/flink-connector-argo-1.15.4.jar temp
cd "./temp"
jar -xf flink-connector-argo-1.15.4.jar
rm -rf org/xerial/snappy
rm -f *.jar
jar cfM ../flink-connector-argo-1.15.4.jar ./