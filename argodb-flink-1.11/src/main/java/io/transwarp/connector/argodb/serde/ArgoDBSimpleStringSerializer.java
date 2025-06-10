package io.transwarp.connector.argodb.serde;


public class ArgoDBSimpleStringSerializer implements ArgoDBRecordSerializationSchema<String[]> {


  @Override
  public byte[][] serialize(String[] element, Long timestamp) {
    return new byte[0][];
  }

  @Override
  public String[] serialize(String[] element) {
    String[] res = new String[element.length];
    for (int i = 0; i < element.length; i++) {
      res[i] = element[i];
    }
    return res;
  }

}
